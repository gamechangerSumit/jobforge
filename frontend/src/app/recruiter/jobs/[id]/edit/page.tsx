'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {
  getJob,
  updateJob,
  publishJob,
  unpublishJob,
  closeJob,
} from '@/lib/api/jobs';
import { ApiClientError } from '@/lib/api/client';
import { buildJobPayload, isoToLocalInput, jobSchema, localInputToIso } from '@/lib/validation/jobs';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { z } from 'zod';

type Form = z.infer<typeof jobSchema>;

export default function EditJobPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ['job', id],
    queryFn: () => getJob(id),
    enabled: Boolean(id),
  });

  const [form, setForm] =
    useState<Form | undefined>();

  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!q.data) {
      return;
    }

    const data = q.data;
    setForm({
      title: data.title,
      description: data.description,
      requirements: data.requirements ?? '',
      benefits: data.benefits ?? '',
      employmentType: data.employmentType,
      workMode: data.workMode,
      experienceLevel: data.experienceLevel,
      // The backend omits location/salary when unset (e.g. remote jobs), so every access is null-safe.
      city: data.location?.city ?? '',
      state: data.location?.state ?? '',
      country: data.location?.country ?? '',
      salaryMin: data.salary?.min ?? undefined,
      salaryMax: data.salary?.max ?? undefined,
      currency: data.salary?.currency ?? 'INR',
      salaryPeriod: data.salary?.period ?? 'YEAR',
      salaryVisible: data.salaryVisible,
      openings: data.openings,
      // datetime-local needs local wall-clock time, not the UTC string from the API.
      expiresAt: isoToLocalInput(data.expiresAt),
      skills: data.skills.map((skill) => skill.name).join(', '),
    });
  }, [q.data]);

  /*
   * Loading state
   */
  if (q.isLoading) {
    return (
      <main className="mx-auto max-w-4xl px-4 py-10">
        Loading job…
      </main>
    );
  }

  /*
   * API error
   */
  if (q.error) {
    return (
      <main
        className="mx-auto max-w-4xl px-4 py-10"
        role="alert"
      >
        Job could not be loaded.
      </main>
    );
  }

  /*
   * Important:
   * TypeScript now knows job definitely exists.
   */
  const job = q.data;

  if (!job) {
    return (
      <main className="mx-auto max-w-4xl px-4 py-10">
        Job not found.
      </main>
    );
  }

  /*
   * Form can theoretically still be undefined while
   * useEffect is waiting for the state update.
   */
  if (!form) {
    return (
      <main className="mx-auto max-w-4xl px-4 py-10">
        Preparing editor…
      </main>
    );
  }

  const set = <K extends keyof Form>(
    key: K,
    value: Form[K],
  ) => {
    setForm((previous) => {
      if (!previous) {
        return previous;
      }

      return {
        ...previous,
        [key]: value,
      };
    });
  };

  const submit = async (
    e: React.FormEvent<HTMLFormElement>,
  ) => {
    e.preventDefault();

    setError('');

    const parsed =
      jobSchema.safeParse(form);

    if (!parsed.success) {
      setError(
        parsed.error.issues[0]?.message ??
          'Please check the form.',
      );

      return;
    }

    setBusy(true);

    try {
      const payload = buildJobPayload(parsed.data);
      // Keep the required/optional flag of skills that already exist on the job; new ones default to required.
      const requiredByName = new Map(job.skills.map((skill) => [skill.name.toLowerCase(), skill.required]));
      const { aiRequestId: _ignored, ...base } = payload; // aiRequestId is not patchable (API_CONTRACT 12.4)
      void _ignored;
      const next = await updateJob(
        id,
        {
          ...base,
          // PATCH is a merge: '' clears a text field, null clears an optional object.
          requirements: parsed.data.requirements.trim(),
          benefits: parsed.data.benefits.trim(),
          location: payload.location ?? (parsed.data.workMode === 'REMOTE' ? null : undefined),
          salary: payload.salary ?? null,
          expiresAt: localInputToIso(parsed.data.expiresAt),
          skills: payload.skills.map((skill) => ({
            name: skill.name,
            required: requiredByName.get(skill.name.toLowerCase()) ?? true,
          })),
        },
        job.version,
      );

      qc.setQueryData(
        ['job', id],
        next,
      );

      await qc.invalidateQueries({
        queryKey: ['recruiter-jobs'],
      });

      router.push(
        '/recruiter/jobs',
      );
    } catch (e) {
      setError(
        e instanceof ApiClientError &&
          e.status === 409
          ? 'This job was changed by someone else. Reload the latest version before saving.'
          : e instanceof ApiClientError
            ? e.message
            : 'Unable to save changes.',
      );
    } finally {
      setBusy(false);
    }
  };

  const action = async (
    fn: (
      id: string,
      version: number,
    ) => Promise<unknown>,
  ) => {
    setBusy(true);
    setError('');

    try {
      const next = await fn(
        id,
        job.version,
      );

      qc.setQueryData(
        ['job', id],
        next,
      );

      await qc.invalidateQueries({
        queryKey: ['recruiter-jobs'],
      });
    } catch (e) {
      setError(
        e instanceof ApiClientError
          ? e.message
          : 'Action failed.',
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="mx-auto max-w-4xl px-4 py-10">
      <Card>
        <div className="flex flex-col justify-between gap-4 md:flex-row md:items-center">
          <div>
            <h1 className="text-2xl font-black">
              Edit job
            </h1>

            <p className="text-sm text-slate-500">
              Current status: {job.status}
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {(job.status === 'DRAFT' || job.status === 'UNPUBLISHED' || job.status === 'CLOSED' || job.status === 'EXPIRED') && (
              <Button
                disabled={busy}
                onClick={() =>
                  void action(publishJob)
                }
              >
                Publish
              </Button>
            )}

            {job.status === 'PUBLISHED' && (
              <>
                <Button
                  disabled={busy}
                  onClick={() =>
                    void action(
                      unpublishJob,
                    )
                  }
                >
                  Unpublish
                </Button>

                <Button
                  disabled={busy}
                  onClick={() =>
                    void action(closeJob)
                  }
                >
                  Close
                </Button>
              </>
            )}
          </div>
        </div>

        <form
          onSubmit={submit}
          className="mt-6 space-y-5"
        >
          <div className="grid gap-4 md:grid-cols-3">
            <Input
              label="Title"
              value={form.title}
              onChange={(value) =>
                set('title', value)
              }
            />

            <SelectField
              label="Employment type"
              value={form.employmentType}
              options={[
                'FULL_TIME',
                'PART_TIME',
                'CONTRACT',
                'INTERNSHIP',
                'FREELANCE',
              ]}
              onChange={(value) =>
                set(
                  'employmentType',
                  value as Form['employmentType'],
                )
              }
            />

            <SelectField
              label="Work mode"
              value={form.workMode}
              options={[
                'ONSITE',
                'HYBRID',
                'REMOTE',
              ]}
              onChange={(value) =>
                set(
                  'workMode',
                  value as Form['workMode'],
                )
              }
            />
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <SelectField
              label="Experience level"
              value={form.experienceLevel}
              options={[
                'INTERN',
                'ENTRY',
                'MID',
                'SENIOR',
                'LEAD',
                'EXECUTIVE',
              ]}
              onChange={(value) =>
                set(
                  'experienceLevel',
                  value as Form['experienceLevel'],
                )
              }
            />

            <Input
              label="Openings"
              type="number"
              value={String(form.openings)}
              onChange={(value) =>
                set(
                  'openings',
                  Number(value),
                )
              }
            />

            <Input
              label="Expires at"
              type="datetime-local"
              value={
                form.expiresAt?.slice(
                  0,
                  16,
                ) ?? ''
              }
              onChange={(value) =>
                set(
                  'expiresAt',
                  value,
                )
              }
            />
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <Input
              label="City"
              value={form.city ?? ''}
              onChange={(value) =>
                set('city', value)
              }
            />

            <Input
              label="State"
              value={form.state ?? ''}
              onChange={(value) =>
                set('state', value)
              }
            />

            <Input
              label="Country"
              value={form.country ?? ''}
              onChange={(value) =>
                set('country', value)
              }
            />
          </div>

          <TextArea
            label="Description (Markdown)"
            value={form.description}
            onChange={(value) =>
              set(
                'description',
                value,
              )
            }
          />

          <TextArea
            label="Requirements (Markdown)"
            value={form.requirements}
            onChange={(value) =>
              set(
                'requirements',
                value,
              )
            }
          />

          <TextArea
            label="Benefits (Markdown)"
            value={form.benefits}
            onChange={(value) =>
              set(
                'benefits',
                value,
              )
            }
          />

          <div className="grid gap-4 md:grid-cols-4">
            <Input
              label="Salary minimum"
              type="number"
              value={
                form.salaryMin === undefined
                  ? ''
                  : String(form.salaryMin)
              }
              onChange={(value) =>
                set(
                  'salaryMin',
                  value === ''
                    ? undefined
                    : Number(value),
                )
              }
            />

            <Input
              label="Salary maximum"
              type="number"
              value={
                form.salaryMax === undefined
                  ? ''
                  : String(form.salaryMax)
              }
              onChange={(value) =>
                set(
                  'salaryMax',
                  value === ''
                    ? undefined
                    : Number(value),
                )
              }
            />

            <Input
              label="Currency"
              value={
                form.currency ?? 'INR'
              }
              onChange={(value) =>
                set(
                  'currency',
                  value,
                )
              }
            />

            <SelectField
              label="Salary period"
              value={
                form.salaryPeriod ??
                'YEAR'
              }
              options={[
                'YEAR',
                'MONTH',
                'HOUR',
              ]}
              onChange={(value) =>
                set(
                  'salaryPeriod',
                  value as Form['salaryPeriod'],
                )
              }
            />
          </div>

          <label className="flex items-center gap-2 text-sm font-semibold">
            <input
              type="checkbox"
              checked={form.salaryVisible}
              onChange={(event) =>
                set(
                  'salaryVisible',
                  event.target.checked,
                )
              }
            />

            Show salary publicly
          </label>

          <Input
            label="Skills (comma separated)"
            value={form.skills}
            onChange={(value) =>
              set(
                'skills',
                value,
              )
            }
          />

          {error && (
            <p
              role="alert"
              className="rounded-lg bg-red-50 p-3 text-sm text-red-700"
            >
              {error}
            </p>
          )}

          <Button disabled={busy}>
            {busy
              ? 'Saving…'
              : 'Save changes'}
          </Button>
        </form>
      </Card>
    </main>
  );
}

function SelectField({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  return (
    <label className="block text-sm font-semibold">
      {label}

      <select
        value={value}
        onChange={(event) =>
          onChange(
            event.target.value,
          )
        }
        className="mt-1 min-h-10 w-full rounded-lg border px-3 font-normal"
      >
        {options.map((option) => (
          <option
            key={option}
            value={option}
          >
            {option.replaceAll(
              '_',
              ' ',
            )}
          </option>
        ))}
      </select>
    </label>
  );
}

function Input({
  label,
  value,
  onChange,
  type = 'text',
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
  return (
    <label className="block text-sm font-semibold">
      {label}

      <input
        type={type}
        value={value}
        onChange={(event) =>
          onChange(
            event.target.value,
          )
        }
        className="mt-1 min-h-10 w-full rounded-lg border px-3 font-normal"
      />
    </label>
  );
}

function TextArea({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="block text-sm font-semibold">
      {label}

      <textarea
        rows={7}
        value={value}
        onChange={(event) =>
          onChange(
            event.target.value,
          )
        }
        className="mt-1 w-full rounded-lg border p-3 font-normal"
      />
    </label>
  );
}