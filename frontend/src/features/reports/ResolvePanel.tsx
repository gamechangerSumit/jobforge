
'use client';

import { useId, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { Button } from '@/components/ui/Button';
import { Select } from '@/components/ui/Select';
import { ApiClientError } from '@/lib/api/client';
import { errorMessage } from '@/lib/api/errors';
import {
  ACTION_HELP,
  ACTION_LABEL,
  DESTRUCTIVE,
  allowedActions,
} from '@/lib/reports/rules';
import {
  resolveFormSchema,
  type ResolveFormInput,
  type ResolveFormOutput,
} from '@/lib/validation/reports';
import { useResolveReport } from './hooks';
import type { AdminReportDetail, ModerationAction } from '@/types/reports';

function resolveErrorMessage(e: unknown): string {
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

  if (status === 403) {
    return 'You do not have permission to moderate reports.';
  }

  if (status === 404) {
    return 'This report no longer exists.';
  }

  if (status === 409) {
    return 'This report was already closed or the item changed. Reload to see the latest state.';
  }

  if (status === 422 && message) {
    return message;
  }

  return errorMessage(
    e,
    'The decision could not be saved. Please try again.',
  );
}

/**
 * Decision form. Offers only the actions the backend accepts
 * for this report; the API remains the authority.
 */
export function ResolvePanel({
  report,
}: {
  report: AdminReportDetail;
}) {
  const id = useId();
  const resolve = useResolveReport(report.id);
  const [serverError, setServerError] = useState('');
  const [done, setDone] = useState<ModerationAction | null>(null);

  const actions = allowedActions({
    status: report.status,
    targetType: report.targetType,
    targetAvailable: report.target.available,
  });

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ResolveFormInput, unknown, ResolveFormOutput>({
    resolver: zodResolver(resolveFormSchema),
    defaultValues: {
      action: undefined,
      reason: '',
      confirmed: false,
    },
  });

  const selected = watch('action') as ModerationAction | undefined;
  const busy = isSubmitting || resolve.isPending;

  if (done) {
    return (
      <p
        role="status"
        className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900"
      >
        Decision saved: {ACTION_LABEL[done]}.
      </p>
    );
  }

  if (actions.length === 0) {
    return (
      <p className="text-sm text-slate-600">
        This report is closed. No further decision is possible.
      </p>
    );
  }

  const submit = handleSubmit(async (values) => {
    setServerError('');

    try {
      await resolve.mutateAsync({
        action: values.action,
        reason: values.reason,
      });

      setDone(values.action);
    } catch (e) {
      setServerError(resolveErrorMessage(e));
    }
  });

  return (
    <form
      onSubmit={(event) => void submit(event)}
      noValidate
      aria-label="Moderation decision"
      className="space-y-3"
    >
      {!report.target.available && (
        <p className="rounded-lg bg-amber-50 p-2 text-xs text-amber-900">
          The reported item no longer exists, so the report can only be
          dismissed.
        </p>
      )}

      <div>
        <label
          htmlFor={`${id}-action`}
          className="block text-sm font-semibold"
        >
          Decision
        </label>

        <Select
          id={`${id}-action`}
          defaultValue=""
          aria-invalid={errors.action ? true : undefined}
          {...register('action')}
        >
          <option value="" disabled>
            Select a decision…
          </option>

          {actions.map((action) => (
            <option key={action} value={action}>
              {ACTION_LABEL[action]}
            </option>
          ))}
        </Select>

        {selected && (
          <p className="mt-1 text-xs text-slate-600">
            {ACTION_HELP[selected]}
          </p>
        )}

        {errors.action && (
          <p role="alert" className="mt-1 text-xs text-red-600">
            {errors.action.message}
          </p>
        )}
      </div>

      <div>
        <label
          htmlFor={`${id}-reason`}
          className="block text-sm font-semibold"
        >
          Reason{' '}
          <span className="font-normal text-slate-500">
            (10–500 characters, recorded in the audit log)
          </span>
        </label>

        <textarea
          id={`${id}-reason`}
          rows={3}
          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-slate-900 focus:ring-2 focus:ring-slate-200"
          {...register('reason')}
        />

        {errors.reason && (
          <p role="alert" className="mt-1 text-xs text-red-600">
            {errors.reason.message}
          </p>
        )}
      </div>

      {selected && DESTRUCTIVE.includes(selected) && (
        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="mt-1"
            {...register('confirmed')}
          />
          <span>
            I confirm that I want to apply “{ACTION_LABEL[selected]}”.
            This changes the live item.
          </span>
        </label>
      )}

      {errors.confirmed && (
        <p role="alert" className="text-xs text-red-600">
          {errors.confirmed.message}
        </p>
      )}

      {serverError && (
        <p role="alert" className="text-sm text-red-600">
          {serverError}
        </p>
      )}

      <Button
        type="submit"
        variant={
          selected && DESTRUCTIVE.includes(selected) ? 'danger' : 'default'
        }
        disabled={busy}
      >
        {busy ? 'Saving…' : 'Save decision'}
      </Button>
    </form>
  );
}
