'use client';

import { useState } from 'react';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';

import {
  createApplicationNote,
  deleteApplicationNote,
  listApplicationNotes,
  updateApplicationNote,
} from '@/lib/api/applications';

import type { ApplicationNote } from '@/types/api';

import { Button } from '@/components/ui/Button';

interface ApplicationNotesProps {
  applicationId: string;
}

export function ApplicationNotes({
  applicationId,
}: ApplicationNotesProps) {
  const qc = useQueryClient();

  const [body, setBody] = useState('');
  const [editing, setEditing] =
    useState<string | null>(null);
  const [error, setError] = useState<string | null>(
    null,
  );

  const q = useQuery({
    queryKey: [
      'application-notes',
      applicationId,
    ],
    queryFn: () =>
      listApplicationNotes(applicationId),
  });

  const save = useMutation({
    mutationFn: async () => {
      const value = body.trim();

      if (!value) {
        throw new Error(
          'Note cannot be empty.',
        );
      }

      if (editing) {
        return updateApplicationNote(
          applicationId,
          editing,
          { body: value },
        );
      }

      return createApplicationNote(
        applicationId,
        { body: value },
      );
    },

    onSuccess: () => {
      setBody('');
      setEditing(null);
      setError(null);

      void qc.invalidateQueries({
        queryKey: [
          'application-notes',
          applicationId,
        ],
      });
    },

    onError: (err) => {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to save note.',
      );
    },
  });

  const remove = useMutation({
    mutationFn: (noteId: string) =>
      deleteApplicationNote(
        applicationId,
        noteId,
      ),

    onSuccess: () => {
      void qc.invalidateQueries({
        queryKey: [
          'application-notes',
          applicationId,
        ],
      });
    },

    onError: () => {
      setError(
        'Unable to delete note.',
      );
    },
  });

  const notes: ApplicationNote[] =
    q.data ?? [];

  return (
    <section className="mt-8 rounded-2xl border bg-white p-5">
      <h2 className="text-lg font-bold">
        Internal notes
      </h2>

      <textarea
        aria-label="Application note"
        maxLength={2000}
        rows={4}
        value={body}
        onChange={(event) =>
          setBody(event.target.value)
        }
        className="mt-3 w-full rounded-lg border p-3"
        placeholder="Private recruiter note…"
      />

      <div className="mt-2 flex items-center gap-2">
        <Button
          disabled={
            !body.trim() ||
            save.isPending
          }
          onClick={() =>
            void save.mutate()
          }
        >
          {editing
            ? 'Update note'
            : 'Add note'}
        </Button>

        {editing && (
          <Button
            className="bg-white text-slate-900 ring-1 ring-slate-300"
            onClick={() => {
              setEditing(null);
              setBody('');
              setError(null);
            }}
          >
            Cancel
          </Button>
        )}

        <span className="text-xs text-slate-500">
          {body.length}/2000
        </span>
      </div>

      {error && (
        <p
          role="alert"
          className="mt-2 text-sm text-red-600"
        >
          {error}
        </p>
      )}

      <div className="mt-5 space-y-3">
        {q.isLoading && (
          <p className="text-sm text-slate-500">
            Loading notes…
          </p>
        )}

        {q.isError && (
          <p
            role="alert"
            className="text-sm text-red-600"
          >
            Unable to load notes.
          </p>
        )}

        {notes.map(
          (note: ApplicationNote) => (
            <div
              key={note.id}
              className="rounded-lg bg-slate-50 p-3"
            >
              <p className="whitespace-pre-wrap text-sm">
                {note.body}
              </p>

              <p className="mt-2 text-xs text-slate-500">
                {new Date(
                  note.updatedAt,
                ).toLocaleString()}
              </p>

              <div className="mt-2 flex gap-2">
                <button
                  type="button"
                  className="text-xs font-semibold underline"
                  onClick={() => {
                    setEditing(note.id);
                    setBody(note.body);
                    setError(null);
                  }}
                >
                  Edit
                </button>

                <button
                  type="button"
                  className="text-xs font-semibold text-red-700 underline"
                  disabled={remove.isPending}
                  onClick={() => {
                    if (
                      window.confirm(
                        'Delete this note?',
                      )
                    ) {
                      void remove.mutate(
                        note.id,
                      );
                    }
                  }}
                >
                  Delete
                </button>
              </div>
            </div>
          ),
        )}

        {!q.isLoading &&
          !q.isError &&
          notes.length === 0 && (
            <p className="text-sm text-slate-500">
              No internal notes.
            </p>
          )}
      </div>
    </section>
  );
}