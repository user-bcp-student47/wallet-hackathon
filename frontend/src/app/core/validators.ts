import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Código OTP fijo de prueba. La verificación es simulada y solo ocurre en el frontend. */
export const OTP_TEST_CODE = '123456';

/** Adapta una función de validación al `ValidatorFn` de los formularios reactivos. */
export function toValidator(check: (value: string) => string | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const message = check(String(control.value ?? ''));
    return message ? { message } : null;
  };
}

const REQUIRED = 'Este campo es obligatorio.';

export function phoneError(value: string): string | null {
  const v = value.trim();
  if (!v) return REQUIRED;
  if (!/^9\d{8}$/.test(v)) return 'Ingresa un celular de 9 dígitos que empiece por 9. Ej.: 912345678.';
  return null;
}

export function otpError(value: string): string | null {
  const v = value.trim();
  if (!v) return REQUIRED;
  if (v !== OTP_TEST_CODE) return `Código incorrecto. Ingresa el código de prueba ${OTP_TEST_CODE}.`;
  return null;
}

export function fullNameError(value: string): string | null {
  const v = value.trim();
  if (!v) return REQUIRED;
  if (v.split(/\s+/).filter((w) => w.length >= 2).length < 2) {
    return 'Ingresa tu nombre completo: al menos un nombre y un apellido.';
  }
  if (v.length > 120) return 'El nombre es demasiado largo (máximo 120 caracteres).';
  return null;
}

/** `value` es una fecha AAAA-MM-DD, como la entrega un campo de tipo date. */
export function birthDateError(value: string, today: Date = new Date()): string | null {
  const v = value.trim();
  if (!v) return REQUIRED;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v);
  if (!match) return 'Ingresa una fecha válida con formato AAAA-MM-DD.';
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    return 'Ingresa una fecha válida con formato AAAA-MM-DD.';
  }
  const todayUtc = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  if (date.getTime() > todayUtc) return 'La fecha de nacimiento no puede ser futura.';
  let age = today.getFullYear() - year;
  const birthdayPassed = today.getMonth() + 1 > month || (today.getMonth() + 1 === month && today.getDate() >= day);
  if (!birthdayPassed) age -= 1;
  if (age < 18) return 'Debes ser mayor de edad (18 años o más) para registrarte.';
  return null;
}

export function documentError(value: string): string | null {
  const v = value.trim();
  if (!v) return REQUIRED;
  if (!/^TEST-.+/.test(v)) return 'Usa un documento de prueba que empiece por TEST-. Ej.: TEST-001.';
  if (v.length > 200) return 'El documento es demasiado largo (máximo 200 caracteres).';
  return null;
}
