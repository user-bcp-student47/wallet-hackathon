import { formatDuration, formatMetric } from './format';

describe('format', () => {
  it('formats durations', () => {
    expect(formatDuration(450)).toBe('450 ms');
    expect(formatDuration(45_000)).toBe('45 s');
    expect(formatDuration(125_000)).toBe('2 min 5 s');
    expect(formatDuration(180_000)).toBe('3 min 0 s');
  });

  it('formats each metric state', () => {
    expect(formatMetric({ millis: 61_000, state: 'MEASURED' })).toBe('1 min 1 s');
    expect(formatMetric({ millis: null, state: 'PENDING' })).toBe('Pendiente');
    expect(formatMetric({ millis: null, state: 'NOT_AVAILABLE' })).toBe('—');
  });
});
