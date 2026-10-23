import {
  birthDateError,
  documentError,
  fullNameError,
  OTP_TEST_CODE,
  otpError,
  phoneError,
} from './validators';

describe('validators', () => {
  describe('phoneError', () => {
    it('accepts a 9-digit number starting with 9', () => {
      expect(phoneError('912345678')).toBeNull();
    });

    it.each(['', '   '])('requires a value (%j)', (v) => {
      expect(phoneError(v)).toContain('obligatorio');
    });

    it.each(['812345678', '91234567', '9123456789', '9123abc78'])('rejects invalid format (%s)', (v) => {
      expect(phoneError(v)).toContain('9 dígitos');
    });
  });

  describe('otpError (simulado)', () => {
    it('accepts the fixed test code', () => {
      expect(otpError(OTP_TEST_CODE)).toBeNull();
      expect(OTP_TEST_CODE).toBe('123456');
    });

    it('requires a value', () => {
      expect(otpError('')).toContain('obligatorio');
    });

    it('rejects a wrong code and tells the user the test code', () => {
      expect(otpError('000000')).toContain('incorrecto');
      expect(otpError('000000')).toContain(OTP_TEST_CODE);
    });
  });

  describe('fullNameError', () => {
    it('accepts a name with at least two words', () => {
      expect(fullNameError('Ana Pérez')).toBeNull();
    });

    it('requires a value', () => {
      expect(fullNameError(' ')).toContain('obligatorio');
    });

    it('rejects a single word', () => {
      expect(fullNameError('Ana')).toContain('nombre completo');
    });
  });

  describe('birthDateError', () => {
    const today = new Date(2026, 9, 23); // 23-oct-2026

    it('accepts someone who turned 18 today', () => {
      expect(birthDateError('2008-10-23', today)).toBeNull();
    });

    it('accepts someone older than 21 (age is not capped)', () => {
      expect(birthDateError('1990-01-01', today)).toBeNull();
    });

    it('rejects someone turning 18 tomorrow', () => {
      expect(birthDateError('2008-10-24', today)).toContain('mayor de edad');
    });

    it('rejects a minor', () => {
      expect(birthDateError('2015-05-05', today)).toContain('mayor de edad');
    });

    it('rejects a future date', () => {
      expect(birthDateError('2026-10-24', today)).toContain('futura');
    });

    it.each(['2000-02-31', '2000-13-01', 'abc', '01/01/2000'])('rejects an invalid date (%s)', (v) => {
      expect(birthDateError(v, today)).toContain('AAAA-MM-DD');
    });

    it('requires a value', () => {
      expect(birthDateError('', today)).toContain('obligatorio');
    });
  });

  describe('documentError', () => {
    it('accepts a TEST- document', () => {
      expect(documentError('TEST-001')).toBeNull();
    });

    it('requires a value', () => {
      expect(documentError('')).toContain('obligatorio');
    });

    it.each(['12345678', 'test-001', 'TEST-', 'XTEST-1'])('rejects a document without the TEST- prefix (%s)', (v) => {
      expect(documentError(v)).toContain('TEST-');
    });
  });
});
