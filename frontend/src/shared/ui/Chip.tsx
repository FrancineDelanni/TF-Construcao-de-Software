import { labels } from "./statusLabels";
export function Chip({ status }: { status: string }) {
  return (
    <span className={`chip ${status.toLowerCase()}`}>
      {labels[status] ?? status}
    </span>
  );
}
