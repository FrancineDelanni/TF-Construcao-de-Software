import { useEffect, useRef, useState } from "react";
import { remainingSeconds, formatCountdown } from "./reservationClock";
import { date } from "../../shared/formatting";
export function Countdown({
  snapshot,
  onZero,
}: {
  snapshot: { expiresAt: string; serverNow: string };
  onZero: () => void;
}) {
  const receivedAt = useRef(performance.now());
  const [elapsed, setElapsed] = useState(0);
  const zeroNotified = useRef(false);
  useEffect(() => {
    receivedAt.current = performance.now();
    setElapsed(0);
    zeroNotified.current = false;
  }, [snapshot.serverNow, snapshot.expiresAt]);
  useEffect(() => {
    const timer = setInterval(
      () => setElapsed(performance.now() - receivedAt.current),
      250,
    );
    return () => clearInterval(timer);
  }, []);
  const seconds = remainingSeconds(
    snapshot.expiresAt,
    snapshot.serverNow,
    elapsed,
  );
  useEffect(() => {
    if (seconds === 0 && !zeroNotified.current) {
      zeroNotified.current = true;
      onZero();
    }
  }, [seconds, onZero]);
  return (
    <div className={`countdown ${seconds < 60 ? "urgent" : ""}`}>
      <div>
        <span className="eyebrow">TEMPO DA RESERVA</span>
        <strong role="timer" aria-label="Tempo restante">
          {formatCountdown(seconds)}
        </strong>
      </div>
      <p>
        {seconds
          ? "Seus assentos estão reservados. Conclua dentro do prazo."
          : "Prazo no relógio encerrado. Consultando a decisão do servidor…"}
        <small>
          Horário de referência do servidor: {date(snapshot.serverNow)}
        </small>
      </p>
    </div>
  );
}
