
'use client';

import { useId, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { ApiClientError } from '@/lib/api/client';
import { errorMessage, fieldErrors } from '@/lib/api/errors';
import { REASONS, REASON_LABEL, TARGET_LABEL } from '@/lib/reports/rules';
import {
  reportFormSchema,
  type ReportFormInput,
  type ReportFormOutput,
} from '@/lib/validation/reports';
import { useCreateReport } from './hooks';
import type { ReportTargetType } from '@/types/reports';

export function reportErrorMessage(e: unknown, target: string): string {
  const apiError =
    typeof e === 'object' && e !== null
      ? (e as {
          status?: number;
          error?: { message?: string };
          message?: string;
        })
      : null;

  const status =
    e instanceof ApiClientError ? e.status : apiError?.status;

  const message =
    e instanceof ApiClientError
      ? e.error.message
      : apiError?.error?.message ?? apiError?.message;

  if (status === 409) {
    return `You already have an open report for this ${target}. We will review it.`;
  }

  if (status === 404) {
    return `This ${target} is no longer available to report.`;
  }

  if (status === 429) {
    return 'You are reporting too quickly. Please wait a moment and try again.';
  }

  if (status === 401) {
    return 'Please sign in again to send a report.';
  }

  if (status === 422 && message) {
    return message;
  }

  return errorMessage(
    e,
    'The report could not be sent. Please try again.',
  );
}

export function ReportForm({
  targetType,
  targetId,
  onDone,
  onCancel,
}: {
  targetType: ReportTargetType;
  targetId: string;
  onDone: () => void;
  onCancel: () => void;
}) {
  const id = useId();
  const create = useCreateReport();
  const [serverError, setServerError] = useState('');
  const noun = TARGET_LABEL[targetType].toLowerCase();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ReportFormInput, unknown, ReportFormOutput>({
    resolver: zodResolver(reportFormSchema),
    defaultValues: { reason: undefined, details: '' },
  });

  const busy = isSubmitting || create.isPending;

  const submit = handleSubmit(async (values) => {
    setServerError('');

    try {
      await create.mutateAsync({
        targetType,
        targetId,
        reason: values.reason,
        ...(values.details ? { details: values.details } : {}),
      });

      onDone();
    } catch (e) {
      const fields = fieldErrors(e);
      let mapped = false;

      (['reason', 'details'] as const).forEach((field) => {
        if (fields[field]) {
          setError(field, { message: fields[field] });
          mapped = true;
        }
      });

      if (!mapped) {
        setServerError(reportErrorMessage(e, noun));
      }
    }
  });

  return (
    <form
      onSubmit={(event) => void submit(event)}
      noValidate
      aria-label={`Report this ${noun}`}
      className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-left"
    >
      <p className="text-sm text-slate-600">
        Tell us what is wrong with this {noun}. Reports are reviewed by our
        team and your details stay private.
      </p>

      <div>
        <label
          htmlFor={`${id}-reason`}
          className="block text-sm font-semibold"
        >
          Reason
        </label>

        <Select
          id={`${id}-reason`}
          defaultValue=""
          aria-invalid={errors.reason ? true : undefined}
          {...register('reason')}
        >
          <option value="" disabled>
            Select a reason…
          </option>

          {REASONS.map((reason) => (
            <option key={reason} value={reason}>
              {REASON_LABEL[reason]}
            </option>
          ))}
        </Select>

        {errors.reason && (
          <p role="alert" className="mt-1 text-xs text-red-600">
            {errors.reason.message}
          </p>
        )}
      </div>

      <div>
        <label
          htmlFor={`${id}-details`}
          className="block text-sm font-semibold"
        >
          Details{' '}
          <span className="font-normal text-slate-500">
            (optional, max 1000 characters)
          </span>
        </label>

        <textarea
          id={`${id}-details`}
          rows={4}
          maxLength={1000}
          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-200"
          {...register('details')}
        />

        {errors.details && (
          <p role="alert" className="mt-1 text-xs text-red-600">
            {errors.details.message}
          </p>
        )}
      </div>

      {serverError && (
        <p role="alert" className="text-sm text-red-600">
          {serverError}
        </p>
      )}

      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Sending…' : 'Send report'}
        </Button>

        <button
          type="button"
          onClick={onCancel}
          className="rounded-lg px-4 py-2 text-sm font-semibold ring-1 ring-slate-300"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
