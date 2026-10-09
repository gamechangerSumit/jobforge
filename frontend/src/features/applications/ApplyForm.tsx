
'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { applicationSchema } from '@/lib/validation/jobs';
import { listResumes, applyToJob } from '@/lib/api/applications';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ApiClientError } from '@/lib/api/client';
import type { ResumeSummary } from '@/types/api';

type Form = z.infer<typeof applicationSchema>;

export function ApplyForm({
  jobId,
  onSuccess,
}: {
  jobId: string;
  onSuccess: (id: string) => void;
}) {
  const [resumes, setResumes] = useState<ResumeSummary[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');

  const {
    register,
    handleSubmit,
    setValue,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<Form>({
    resolver: zodResolver(applicationSchema),
    defaultValues: {
      resumeId: '',
    },
  });

  useEffect(() => {
    let active = true;

    listResumes()
      .then((list) => {
        if (!active) return;
        setResumes(list);
      })
      .catch(() => {
        if (active) {
          setError('Could not load your resumes.');
        }
      })
      .finally(() => {
        if (active) {
          setLoaded(true);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (resumes.length === 0 || getValues('resumeId')) {
      return;
    }

    const primary =
      resumes.find((resume) => resume.primary) ??
      (resumes.length === 1 ? resumes[0] : undefined);

    if (primary) {
      setValue('resumeId', primary.id);
    }
  }, [resumes, getValues, setValue]);

  const submit = async (form: Form) => {
    setError('');

    try {
      const application = await applyToJob(jobId, form);
      onSuccess(application.id);
    } catch (e) {
      setError(
        e instanceof ApiClientError
          ? e.message
          : 'Application could not be submitted.',
      );
    }
  };

  const noResume = loaded && resumes.length === 0 && !error;

  return (
    <Card>
      <h1 className="text-xl font-bold">Apply for this job</h1>

      {noResume && (
        <p
          role="status"
          className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900"
        >
          You need a resume before you can apply.{' '}
          <Link
            href="/profile"
            className="font-semibold underline"
          >
            Upload one on your profile
          </Link>
          , then come back.
        </p>
      )}

      <form
        onSubmit={handleSubmit(submit)}
        className="mt-5 space-y-5"
      >
        <div>
          <label
            className="mb-1 block text-sm font-semibold"
            htmlFor="resumeId"
          >
            Resume
          </label>

          <select
            id="resumeId"
            {...register('resumeId')}
            className="min-h-10 w-full rounded-lg border px-3"
          >
            <option value="">Select a resume</option>

            {resumes.map((resume) => (
              <option key={resume.id} value={resume.id}>
                {resume.fileName}
                {resume.primary ? ' · Primary' : ''}
              </option>
            ))}
          </select>

          {errors.resumeId && (
            <p className="mt-1 text-sm text-red-600">
              {errors.resumeId.message}
            </p>
          )}
        </div>

        <div>
          <label
            className="mb-1 block text-sm font-semibold"
            htmlFor="coverLetter"
          >
            Cover letter (optional)
          </label>

          <textarea
            id="coverLetter"
            rows={10}
            {...register('coverLetter')}
            className="w-full rounded-lg border p-3"
            placeholder="Tell the recruiter why you are a strong fit."
          />

          {errors.coverLetter && (
            <p className="mt-1 text-sm text-red-600">
              {errors.coverLetter.message}
            </p>
          )}
        </div>

        {error && (
          <p role="alert" className="text-sm text-red-600">
            {error}
          </p>
        )}

        <Button
          type="submit"
          disabled={isSubmitting || resumes.length === 0 || !loaded || !!error}
        >
          {isSubmitting ? 'Submitting…' : 'Submit application'}
        </Button>
      </form>
    </Card>
  );
}
