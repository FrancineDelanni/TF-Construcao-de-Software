import type { Item } from "./types";
import { money } from "../../shared/formatting";
export function Items({
  items,
  currency = "BRL",
}: {
  items: Item[];
  currency?: string;
}) {
  return (
    <ul className="items">
      {items.map((item) => (
        <li key={item.seatId}>
          <span>
            {item.sector}
            <small>
              Fileira {item.row} · Assento {item.number}
            </small>
          </span>
          <strong>{money(item.priceCents, currency)}</strong>
        </li>
      ))}
    </ul>
  );
}
