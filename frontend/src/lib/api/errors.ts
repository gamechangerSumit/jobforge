
import { ApiClientError } from './client';

type ApiErrorLike = {
  status?: number;
  message?: string;
  error?: {
    code?: string;
    message?: string;
    details?: Array<{
      field?: string;
      message?: string;
      code?: string;
    }>;
  };
};

function getApiError(e: unknown): ApiErrorLike | null {
  if (typeof e !== 'object' || e === null) {
    return null;
  }

  const value = e as ApiErrorLike;

  if (
    typeof value.status === 'number' ||
    (typeof value.error === 'object' && value.error !== null)
  ) {
    return value;
  }

  return null;
}

export const errorMessage = (e: unknown, fallback: string): string => {
  if (e instanceof ApiClientError) {
    return e.error.message;
  }

  const apiError = getApiError(e);

  return apiError?.error?.message ?? apiError?.message ?? fallback;
};

/** Maps API details to a field -> message record for form libraries. */
export function fieldErrors(e: unknown): Record<string, string> {
  const apiError = getApiError(e);
  const details = apiError?.error?.details ?? [];

  return Object.fromEntries(
    details
      .filter((detail) => detail.field)
      .map((detail) => [
        detail.field as string,
        detail.message ?? detail.code ?? 'Invalid value',
      ]),
  );
}
