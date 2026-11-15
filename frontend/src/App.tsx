import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError, loadSession, logout, onSession } from './api';
import { available, canPay, formatCountdown, OperationKeys, remainingSeconds, terminal } from './behavior';
import type { Event, Item, Order, Outcome, Reservation, Seat, Session, Ticket } from './types';
import './style.css';
const labels: Record<string, string> = {CREATED: 'Criado', RESERVED: 'Reservado', AWAITING_PAYMENT: 'Aguardando confirmação', PAID: 'Pago', EXPIRED: 'Expirado', CANCELLED: 'Cancelado', AVAILABLE: 'Livre', FREE: 'Livre', SOLD: 'Vendido', HELD: 'Reservado', ACTIVE: 'Ativa', DECLINED: 'Recusado', PENDING: 'Pendente', APPROVED: 'Aprovado'};
const money = (cents: number, currency = 'BRL') => new Intl.NumberFormat('pt-BR', {style: 'currency', currency}).format(cents / 100);
const date = (value: string) => new Intl.DateTimeFormat('pt-BR', {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(value));
const message = (error: unknown) => error instanceof Error ? error.message : 'Conexão indisponível. Tente novamente.';
type View = 'events' | 'seats' | 'checkout' | 'orders' | 'tickets';
function Countdown({snapshot, onZero}: {snapshot: {expiresAt: string; serverNow: string}; onZero: () => void}) {
  const receivedAt = useRef(performance.now());
  const [elapsed, setElapsed] = useState(0);
  const zeroNotified = useRef(false);
  useEffect(() => { receivedAt.current = performance.now(); setElapsed(0); zeroNotified.current = false; }, [snapshot.serverNow, snapshot.expiresAt]);
  useEffect(() => { const timer = setInterval(() => setElapsed(performance.now() - receivedAt.current), 250); return () => clearInterval(timer); }, []);
  const seconds = remainingSeconds(snapshot.expiresAt, snapshot.serverNow, elapsed);
  useEffect(() => { if (seconds === 0 && !zeroNotified.current) { zeroNotified.current = true; onZero(); } }, [seconds, onZero]);
  return <div className={`countdown ${seconds < 60 ? 'urgent' : ''}`}><div><span className="eyebrow">TEMPO DA RESERVA</span><strong role="timer" aria-label="Tempo restante">{formatCountdown(seconds)}</strong></div><p>{seconds ? 'Seus assentos estão reservados. Conclua dentro do prazo.' : 'Prazo no relógio encerrado. Consultando a decisão do servidor…'}<small>Horário de referência do servidor: {date(snapshot.serverNow)}</small></p></div>;
}
function Items({items, currency = 'BRL'}: {items: Item[]; currency?: string}) {
  return <ul className="items">{items.map(item => <li key={item.seatId}><span>{item.sector}<small>Fileira {item.row} · Assento {item.number}</small></span><strong>{money(item.priceCents, currency)}</strong></li>)}</ul>;
}
function Chip({status}: {status: string}) { return <span className={`chip ${status.toLowerCase()}`}>{labels[status] ?? status}</span>; }
export default function App() {
  const [session, setSession] = useState<Session>({authenticated: false});
  const [booting, setBooting] = useState(true);
  const [view, setView] = useState<View>('events');
  const [events, setEvents] = useState<Event[]>([]);
  const [event, setEvent] = useState<Event | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [outcome, setOutcome] = useState<Outcome>('APPROVE');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [syncError, setSyncError] = useState('');
  const paymentPendingSeen = useRef(new Set<string>());
  const [search, setSearch] = useState('');
  const keys = useRef(new OperationKeys());
  const payment = useRef<{orderId: string; outcome: Outcome; awaitingCount: number} | null>(null);
  const requestBusy = useRef(false);
  const currentUser = session.authenticated ? session.user.sub : null;
  const isCustomer = session.authenticated && session.user.roles.includes('Cliente');
  useEffect(() => {
    const unsubscribe = onSession(setSession);
    loadSession().catch(e => setError(message(e))).finally(() => setBooting(false));
    return unsubscribe;
  }, []);
  useEffect(() => {
    setEvents([]); setEvent(null); setSelected([]); setReservation(null); setOrder(null); setOrders([]); setTickets([]);
    keys.current = new OperationKeys(); payment.current = null; setView('events');
  }, [currentUser]);
  const fetchEvents = useCallback(async () => { setLoading(true); try { const catalog = await api<Event[]>('/api/inventory/events'); setEvents(await Promise.all(catalog.map(value => value.seats ? Promise.resolve(value) : api<Event>(`/api/inventory/events/${value.id}`)))); } finally { setLoading(false); } }, []);
  useEffect(() => { if (currentUser && isCustomer) fetchEvents().catch(e => setError(message(e))); }, [currentUser, isCustomer, fetchEvents]);
  const refreshOrder = useCallback(async () => {
    if (!order) return;
    try {
      const updated = await api<Order>(`/api/orders/${order.id}`);
      setOrder(updated); setSyncError('');
      if (updated.status === 'AWAITING_PAYMENT') paymentPendingSeen.current.add(updated.id);
      if (updated.status === 'RESERVED' && updated.paymentStatus === 'DECLINED' && (paymentPendingSeen.current.has(updated.id) || (payment.current?.orderId === updated.id && (updated.history ?? []).filter(entry => (entry.status ?? entry.state) === 'AWAITING_PAYMENT').length > payment.current.awaitingCount))) {
        paymentPendingSeen.current.delete(updated.id);
        keys.current.complete(`payment:${updated.id}`); payment.current = null;
      }
      if (updated.status === 'PAID') setTickets(await api<Ticket[]>(`/api/delivery/orders/${updated.id}/tickets`));
    } catch (e) { setSyncError(`Atualização interrompida: ${message(e)} A consulta será repetida.`); }
  }, [order?.id]);
  const refreshReservation = useCallback(async () => {
    if (order || !reservation) return;
    try { setReservation(await api<Reservation>(`/api/inventory/reservations/${reservation.reservationId}`)); setSyncError(''); }
    catch (e) { setSyncError(message(e)); }
  }, [order?.id, reservation?.reservationId]);
  useEffect(() => {
    if (view !== 'checkout' && view !== 'tickets') return;
    let active = true;
    const poll = async () => { if (active) { await (order ? refreshOrder() : refreshReservation()); if (active) timer = setTimeout(poll, 2000); } };
    let timer = setTimeout(poll, 2000);
    return () => { active = false; clearTimeout(timer); };
  }, [view, refreshOrder, refreshReservation, order?.id]);
  useEffect(() => {
    if (view !== 'seats' || !event) return;
    let live = true;
    const poll = async () => {
      try { const fresh = await api<Event>(`/api/inventory/events/${event.id}`); if (live) { setEvent(fresh); setSyncError(''); } }
      catch (e) { if (live) setSyncError(`Disponibilidade: ${message(e)}`); }
      if (live) timer = setTimeout(poll, 2000);
    };
    let timer = setTimeout(poll, 2000);
    return () => { live = false; clearTimeout(timer); };
  }, [view, event?.id]);
  async function action(work: () => Promise<void>) {
    if (requestBusy.current) return;
    requestBusy.current = true; setBusy(true); setError('');
    try { await work(); } catch (e) { setError(message(e)); } finally { requestBusy.current = false; setBusy(false); }
  }
  async function openEvent(id: string) {
    await action(async () => { setEvent(await api<Event>(`/api/inventory/events/${id}`)); setSelected([]); setSyncError(''); setView('seats'); });
  }
  async function createOrder(value: Reservation) {
    const created = await api<Order>('/api/orders', {method: 'POST', body: {reservationId: value.reservationId}, key: keys.current.for(`order:${value.reservationId}`)});
    setOrder(created);
  }
  async function reserve() {
    await action(async () => {
      if (!event || !selected.length) return;
      const seatIds = [...selected].sort();
      let reserved: Reservation;
      try {
        reserved = await api<Reservation>(`/api/inventory/events/${event.id}/reservations`, {method: 'POST', body: {seatIds}, key: keys.current.for(`reserve:${event.id}:${seatIds.join(',')}`)});
      } catch (e) {
        if (e instanceof ApiError && e.status === 409) {
          setEvent(await api<Event>(`/api/inventory/events/${event.id}`)); setSelected([]);
          throw new Error('Assentos indisponíveis: outra pessoa acabou de reservá-los. Nenhum assento desta tentativa foi bloqueado. Escolha novamente.');
        }
        throw e;
      }
      setReservation(reserved); setOrder(null); setTickets([]); setView('checkout'); await createOrder(reserved);
    });
  }
  async function pay() {
    await action(async () => {
      if (!order) return;
      if (!payment.current || payment.current.orderId !== order.id) payment.current = {orderId: order.id, outcome, awaitingCount: (order.history ?? []).filter(entry => (entry.status ?? entry.state) === 'AWAITING_PAYMENT').length};
      const updated = await api<Order>(`/api/orders/${order.id}/payments`, {method: 'POST', body: {outcome: payment.current.outcome}, key: keys.current.for(`payment:${order.id}`)});
      setOrder(updated);
      if (updated.status === 'AWAITING_PAYMENT') paymentPendingSeen.current.add(updated.id);
      if (updated.status === 'RESERVED' && updated.paymentStatus === 'DECLINED' && (paymentPendingSeen.current.has(updated.id) || (payment.current?.orderId === updated.id && (updated.history ?? []).filter(entry => (entry.status ?? entry.state) === 'AWAITING_PAYMENT').length > payment.current.awaitingCount))) {
        paymentPendingSeen.current.delete(updated.id); keys.current.complete(`payment:${order.id}`); payment.current = null; }
    });
  }
  async function cancel() {
    await action(async () => {
      if (!order) return;
      setOrder(await api<Order>(`/api/orders/${order.id}/cancel`, {method: 'POST', body: {}, key: keys.current.for(`cancel:${order.id}`)}));
    });
  }
  async function navigate(next: View) {
    await action(async () => {
      setSyncError('');
      if (next === 'events') await fetchEvents();
      if (next === 'orders' || next === 'tickets') setOrders(await api<Order[]>('/api/orders'));
      if (next === 'tickets' && order?.status === 'PAID') setTickets(await api<Ticket[]>(`/api/delivery/orders/${order.id}/tickets`));
      setView(next);
    });
  }
  async function openOrder(value: Order, next: View = 'checkout') {
    await action(async () => {
      setOrder(await api<Order>(`/api/orders/${value.id}`)); setReservation(null); setTickets([]); setView(next);
      if (value.status === 'PAID') setTickets(await api<Ticket[]>(`/api/delivery/orders/${value.id}/tickets`));
    });
  }
  async function download(ticket: Ticket) {
    await action(async () => {
      const blob = await api<Blob>(`/api/delivery/tickets/${ticket.id}/pdf`, {blob: true});
      const url = URL.createObjectURL(blob), link = document.createElement('a'); link.href = url; link.download = `pulsepass-${ticket.id}.pdf`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
  }
  const picked = event?.seats.filter(seat => selected.includes(seat.id)) ?? [];
  const snapshot = order ?? reservation;
  const selectionInvalid = picked.some(seat => !available(seat.status));
  const canCheckout = order && canPay(order.status, order.paymentStatus);
  const grouped = event?.seats.reduce<Record<string, Seat[]>>((all, seat) => { const group = `${seat.sector} · Fileira ${seat.row}`; (all[group] ??= []).push(seat); return all; }, {}) ?? {};
  function ticketList() {
    if (!order || order.status !== 'PAID') return null;
    return <section className="panel tickets"><div className="section-title"><h2>Seus ingressos</h2><span className="live-dot">Emissão assíncrona</span></div>{tickets.length === 0 ? <div className="empty"><span className="spinner"/><h3>Estamos emitindo seus ingressos</h3><p>A compra está confirmada. O PDF aparecerá aqui assim que estiver disponível.</p></div> : tickets.map(ticket => <article className="ticket" key={ticket.id}><div><span className="eyebrow">PULSEPASS · INGRESSO</span><h3>{order.eventName}</h3><p>{ticket.seatDescription}</p><small>Código {ticket.id}</small><p><Chip status={ticket.status}/></p></div><button disabled={busy || !['ISSUED', 'READY', 'VALID', 'ACTIVE'].includes(ticket.status)} onClick={() => download(ticket)}>Baixar PDF ↓</button></article>)}</section>;
  }
  return <><header className="header"><a href="/" className="brand" aria-label="PulsePass início"><span className="brand-mark">p<span>•</span></span>pulsepass<span className="brand-dot">.</span></a>{session.authenticated && <><nav aria-label="Navegação principal">{isCustomer && <><button className={view === 'events' || view === 'seats' ? 'active' : ''} onClick={() => navigate('events')} disabled={busy}>Eventos</button><button className={view === 'orders' || view === 'checkout' ? 'active' : ''} onClick={() => navigate('orders')} disabled={busy}>Meus pedidos</button><button className={view === 'tickets' ? 'active' : ''} onClick={() => navigate('tickets')} disabled={busy}>Ingressos</button></>}</nav><div className="account"><span>{session.user.name}<small>{session.user.roles.filter(role => ['Cliente', 'Organizador', 'Operador', 'Admin'].includes(role)).join(' · ')}</small></span><button className="link" onClick={() => action(logout)} disabled={busy}>Sair ↗</button></div></>}</header>
  <main>{error && <div className="notice error" role="alert"><div><strong>Não foi possível concluir</strong><p>{error}</p></div><button aria-label="Fechar aviso" onClick={() => setError('')}>×</button></div>}{syncError && <div className="notice warning" role="status">{syncError}</div>}
  {booting ? <div className="empty"><span className="spinner"/><h1>Conectando sua sessão…</h1></div> : !session.authenticated ? <section className="welcome"><div><span className="eyebrow">ENCONTROS QUE VIRAM HISTÓRIA</span><h1>O próximo grande<br/>momento começa<br/><em>com você.</em></h1><p>Escolha seu lugar. Viva a experiência.<br/>Seus eventos e ingressos, em um só lugar.</p><a className="button" href="/api/iam/login">Entrar com segurança <span>↗</span></a><small>Autenticação protegida pelo Keycloak</small>{new URLSearchParams(location.search).has('login') && <p className="login-error" role="alert">O acesso não foi concluído. Tente entrar novamente.</p>}</div><aside className="welcome-card"><div className="orbit">✳</div><span className="eyebrow">AMBIENTE DE DEMONSTRAÇÃO</span><h2>Seu lugar está<br/>te esperando.</h2><p>Na tela de acesso, use uma conta fictícia:</p><dl><div><dt>Cliente</dt><dd>cliente1 ou cliente2</dd></div><div><dt>Organizador</dt><dd>organizador</dd></div><div><dt>Operador</dt><dd>operador</dd></div><div><dt>Admin</dt><dd>admin</dd></div><div><dt>Senha local</dt><dd>PulsePass123!</dd></div></dl><p className="fine-print">Pagamentos são simulados. Não informe dados reais de cartão.</p></aside></section> : !isCustomer ? <section className="panel role-notice"><span className="eyebrow">SESSÃO AUTENTICADA</span><h1>Olá, {session.user.name}.</h1><p>Seu papel é {session.user.roles.filter(role => ['Organizador', 'Operador', 'Admin'].includes(role)).join(', ')}. Esta entrega disponibiliza a jornada de compra para contas Cliente.</p><p>Para testar reservas e ingressos, saia e entre como cliente1 ou cliente2.</p></section> : <>
  {view === 'events' && <><section className="page-heading"><div><span className="eyebrow">DESCUBRA. ESCOLHA. VIVA.</span><h1>Seu próximo encontro<span>.</span></h1><p>Experiências para sair da rotina e fazer parte da história.</p></div><span className="edition">PORTO ALEGRE & ALÉM<br/><strong>Agenda PulsePass</strong></span></section><div className="catalog-toolbar"><div><span className="live-dot">Vendas abertas</span><span>{events.length} eventos na agenda</span></div><label className="search"><span>⌕</span><input type="search" aria-label="Buscar eventos" placeholder="Encontre seu evento" value={search} onChange={e => setSearch(e.target.value)}/></label></div>{loading ? <div className="empty"><span className="spinner"/>Carregando eventos…</div> : <div className="event-grid">{events.filter(value => value.name.toLocaleLowerCase().includes(search.toLocaleLowerCase())).map((value, index) => { const seats = value.seats ?? []; const free = seats.filter(seat => available(seat.status)); return <article className="event-card" key={value.id}><div className={`event-art art-${index % 3}`} aria-hidden="true"><span className="art-lines"/><span className="art-symbol">{['✳', '◒', '✴'][index % 3]}</span><span className="art-type">AO VIVO<br/>É DIFERENTE.</span><span className="event-number">0{index + 1} / PULSEPASS</span></div><div className="event-info"><div className="event-date">{date(value.startsAt)}<span className="chip">{free.length ? `${free.length} lugares livres` : 'Esgotado'}</span></div><h2>{value.name}</h2><p className="venue">↗ {value.venue}</p><div className="event-bottom"><div><small>A partir de</small><strong>{free.length ? money(Math.min(...free.map(seat => seat.priceCents))) : '—'}</strong></div><button disabled={busy || !free.length} onClick={() => openEvent(value.id)}>Ver assentos ↗</button></div></div></article>; })}</div>}{!loading && events.length === 0 && <div className="empty"><h2>Nenhum evento disponível</h2><button onClick={() => action(fetchEvents)}>Atualizar agenda</button></div>}</>}
  {view === 'seats' && event && <><button className="back" onClick={() => navigate('events')}>← Voltar aos eventos</button><section className="page-heading"><div><span className="eyebrow">ESCOLHA O SEU LUGAR</span><h1>{event.name}</h1><p>{date(event.startsAt)} · {event.venue}</p></div><span className="step">01 <small>/ 03 · ASSENTOS</small></span></section><div className="seat-layout"><section className="panel map"><div className="section-title"><h2>Mapa de assentos</h2><span className="live-dot">Atualiza a cada 5 s</span></div><div className="stage">PALCO / ÁREA PRINCIPAL</div><div className="legend"><span>□ Livre</span><span>✓ Selecionado</span><span>▨ Reservado</span><span>× Vendido</span></div>{Object.entries(grouped).map(([group, seats]) => <section className="seat-group" key={group}><h3>{group}</h3><div className="seats">{seats.sort((a, b) => Number(a.number) - Number(b.number)).map(seat => <button key={seat.id} disabled={busy || !available(seat.status)} className={`seat ${selected.includes(seat.id) ? 'selected' : seat.status.toLowerCase()}`} aria-pressed={selected.includes(seat.id)} aria-label={`${group}, assento ${seat.number}, ${selected.includes(seat.id) ? 'Selecionado' : labels[seat.status] ?? seat.status}, ${money(seat.priceCents)}`} onClick={() => { setSelected(value => value.includes(seat.id) ? value.filter(id => id !== seat.id) : [...value, seat.id]); setError(''); }}>{selected.includes(seat.id) ? '✓' : seat.status === 'SOLD' ? '×' : seat.number}</button>)}</div></section>)}</section><aside className="panel selection"><span className="eyebrow">SEU RESUMO</span><h2>{picked.length ? `${picked.length} lugar${picked.length > 1 ? 'es' : ''} escolhido${picked.length > 1 ? 's' : ''}` : 'Qual vai ser seu lugar?'}</h2>{picked.length ? <Items items={picked.map(seat => ({...seat, seatId: seat.id}))}/> : <p>Toque em um assento livre no mapa para começar.</p>}<div className="total"><span>Total</span><strong>{money(picked.reduce((sum, seat) => sum + seat.priceCents, 0))}</strong></div><p className="hint">◷ Ao reservar, todos os assentos escolhidos ficam bloqueados por 10 minutos.</p>{selectionInvalid && <p role="alert" className="login-error">A disponibilidade mudou. Desmarque os assentos indisponíveis ou refaça sua seleção.</p>}<button className="primary wide" disabled={busy || !picked.length || selectionInvalid} onClick={reserve}>{busy ? 'Reservando…' : 'Reservar assentos →'}</button><small>Reserva atômica: todos os lugares ou nenhum.</small></aside></div></>}
  {view === 'checkout' && snapshot && <><button className="back" onClick={() => navigate('orders')}>← Meus pedidos</button><section className="page-heading"><div><span className="eyebrow">FALTA POUCO PARA VIVER ESSA EXPERIÊNCIA</span><h1>{order?.status === 'PAID' ? 'Seu lugar está confirmado.' : 'Acompanhe sua compra.'}</h1><p>{snapshot.eventName}</p></div><span className="step">{order?.status === 'PAID' ? '03' : '02'} <small>/ 03 · {order?.status === 'PAID' ? 'CONFIRMADO' : 'CHECKOUT'}</small></span></section>{!terminal(snapshot.status) && <Countdown snapshot={snapshot} onZero={order ? refreshOrder : refreshReservation}/>}<div className="checkout-layout"><section className="panel"><div className="section-title"><h2>Resumo da compra</h2><Chip status={order?.status ?? reservation!.status}/></div><Items items={snapshot.items} currency={snapshot.currency}/><div className="total"><span>Total do pedido</span><strong>{money(snapshot.totalCents, snapshot.currency)}</strong></div><small className="reference">{order ? `Pedido ${order.id}` : `Reserva ${reservation!.reservationId}`}</small>{order?.history?.length ? <div className="history"><h3>Histórico do servidor</h3>{order.history.map((entry, index) => { const at = entry.occurredAt ?? entry.createdAt ?? entry.at; return <div key={index}><span className="history-dot"/><strong>{labels[entry.status ?? entry.state ?? ''] ?? entry.status ?? entry.state}</strong><small>{at ? date(at) : ''}</small></div>; })}</div> : null}</section><section className="panel payment"><span className="eyebrow">{order?.status === 'PAID' ? 'COMPRA CONCLUÍDA' : 'AMBIENTE DE DEMONSTRAÇÃO'}</span>{!order && reservation && ['EXPIRED', 'CANCELLED'].includes(reservation.status) ? <><h2>{reservation.status === 'EXPIRED' ? 'A reserva expirou' : 'Reserva cancelada'}</h2><p>O estado foi confirmado pelo servidor. Escolha novos lugares para começar outra compra.</p><button onClick={() => navigate('events')}>Escolher novos assentos ↗</button></> : !order ? <><h2>Preparando seu pedido</h2><p>A reserva já foi registrada. Estamos vinculando os seus assentos ao pedido.</p><button disabled={busy || reservation?.status !== 'ACTIVE'} onClick={() => action(() => createOrder(reservation!))}>{busy ? 'Preparando…' : 'Consultar / continuar pedido'}</button></> : order.status === 'PAID' ? <><div className="success-icon">✓</div><h2>Pagamento confirmado!</h2><p>O servidor confirmou seus assentos. Seus ingressos estão a caminho desta tela.</p><button onClick={() => setView('tickets')}>Ver meus ingressos ↗</button></> : ['EXPIRED', 'CANCELLED'].includes(order.status) ? <><h2>{order.status === 'EXPIRED' ? 'A reserva expirou' : 'Pedido cancelado'}</h2><p>{order.status === 'EXPIRED' ? 'O servidor confirmou o fim do prazo. Selecione novos lugares para começar outra compra.' : 'O cancelamento foi confirmado pelo servidor.'}</p><button onClick={() => navigate('events')}>Escolher novos assentos ↗</button></> : order.status === 'CREATED' ? <><span className="spinner"/><h2>Vinculando os assentos</h2><p>Seu pedido foi recebido. Consultamos o servidor a cada dois segundos.</p></> : canCheckout ? <><h2>Pagamento simulado</h2><p>Nenhum dado de cartão real é solicitado ou armazenado.</p>{order.paymentStatus === 'DECLINED' && <div className="notice warning" role="status">Pagamento recusado. Seus assentos permanecem reservados pelo prazo restante. Você pode tentar outra simulação.</div>}<label htmlFor="outcome">Resultado do gateway</label><select id="outcome" value={payment.current?.orderId === order.id ? payment.current.outcome : outcome} disabled={busy || payment.current?.orderId === order.id} onChange={e => setOutcome(e.target.value as Outcome)}><option value="APPROVE">Aprovar pagamento</option><option value="DECLINE">Recusar pagamento</option><option value="TIMEOUT">Resposta lenta / timeout</option></select><p className="hint">Resposta lenta mantém a mesma tentativa em processamento. Aguarde a confirmação do servidor.</p><button className="primary wide" disabled={busy} onClick={pay}>{busy ? 'Enviando…' : payment.current?.orderId === order.id ? 'Repetir a mesma tentativa →' : `Simular pagamento · ${money(order.totalCents, order.currency)}`}</button><small>Se a conexão falhar, a tentativa usa a mesma chave para evitar duplicação.</small><button className="link danger" disabled={busy} onClick={cancel}>Solicitar cancelamento da reserva</button></> : <><span className="spinner"/><h2>Aguardando confirmação</h2><p>O pagamento está sendo processado. Mantenha esta tela aberta ou acompanhe em Meus pedidos.</p><div className="pending-status">◷ {labels[order.paymentStatus ?? ''] ?? 'Confirmação dos assentos pendente'}</div><p className="hint">Uma resposta lenta não significa recusa. A consulta continua até o servidor decidir.</p><button className="link danger" disabled={busy} onClick={cancel}>Solicitar cancelamento do pedido</button></>}</section></div>{ticketList()}</>}
  {view === 'orders' && <><section className="page-heading"><div><span className="eyebrow">SUAS EXPERIÊNCIAS</span><h1>Meus pedidos<span>.</span></h1><p>Acompanhe cada etapa, do primeiro clique à confirmação.</p></div><button onClick={() => navigate('orders')} disabled={busy}>Atualizar ↻</button></section><section className="panel order-list">{orders.length === 0 ? <div className="empty"><h2>Seu próximo encontro começa aqui</h2><p>Você ainda não tem pedidos.</p><button onClick={() => navigate('events')}>Explorar eventos ↗</button></div> : orders.map(value => <article className="order-row" key={value.id}><div><small>#{value.id.slice(0, 8)}</small><h2>{value.eventName}</h2><p>{value.items.length} ingresso{value.items.length === 1 ? '' : 's'} · {money(value.totalCents, value.currency)}</p></div><Chip status={value.status}/><button disabled={busy} onClick={() => openOrder(value)}>{value.status === 'RESERVED' ? 'Continuar pagamento' : 'Acompanhar'} ↗</button></article>)}</section></>}
  {view === 'tickets' && <><section className="page-heading"><div><span className="eyebrow">PRONTO PARA O GRANDE DIA</span><h1>Meus ingressos<span>.</span></h1><p>Baixe seu PDF autenticado e guarde para a entrada.</p></div></section>{order?.status === 'PAID' ? ticketList() : null}<section className="panel"><h2>Compras confirmadas</h2>{orders.filter(value => value.status === 'PAID').length === 0 ? <div className="empty"><p>Seus ingressos aparecerão após a confirmação de uma compra.</p><button onClick={() => navigate('orders')}>Acompanhar meus pedidos</button></div> : orders.filter(value => value.status === 'PAID').map(value => <article className="order-row" key={value.id}><div><h3>{value.eventName}</h3><small>Pedido {value.id.slice(0, 8)}</small></div><button disabled={busy} onClick={() => openOrder(value, 'tickets')}>Ver ingressos ↗</button></article>)}</section></>}
  </>}</main><footer><span className="brand">pulsepass.</span><p>Mais do que um ingresso. Um encontro.</p><small>Ambiente local · Dados e pagamentos fictícios</small></footer></>;
}
