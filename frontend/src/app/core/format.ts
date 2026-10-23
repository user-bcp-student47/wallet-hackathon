import { Metric } from './models';

export function formatDuration(millis: number): string {
  if (millis < 1000) return `${millis} ms`;
  const totalSeconds = Math.round(millis / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes === 0) return `${seconds} s`;
  return `${minutes} min ${seconds} s`;
}

export function formatMetric(metric: Metric): string {
  switch (metric.state) {
    case 'MEASURED':
      return formatDuration(metric.millis ?? 0);
    case 'PENDING':
      return 'Pendiente';
    default:
      return '—';
  }
}
