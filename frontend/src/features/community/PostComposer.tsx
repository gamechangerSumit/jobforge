'use client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { MentionInput } from '@/components/common/MentionInput';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { errorMessage, fieldErrors } from '@/lib/api/errors';
import { BODY_MAX, TITLE_MAX, emptyPostForm, postFormSchema, toCreateRequest, metadataFor, parseTags, type PostFormInput, type PostFormOutput } from '@/lib/validation/community';
import { POST_TYPES, type Post } from '@/types/community';
import { useCreatePost, useEditPost } from './hooks';
import { POST_TYPE_LABEL } from './labels';

const toLocal = (iso?: string) => (iso ? new Date(new Date(iso).getTime() - new Date(iso).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : '');

function initialValues(post?: Post): PostFormInput {
  if (!post) return emptyPostForm;
  const m = post.typeMetadata;
  return { ...emptyPostForm, type: post.type, title: post.title ?? '', body: post.body, tags: post.tags.join(' '),
    companyName: m.companyName ?? '', roleTitle: m.roleTitle ?? '', location: m.location ?? '', applyUrl: m.applyUrl ?? '',
    referralDetails: m.referralDetails ?? '', repoUrl: m.repoUrl ?? '', liveUrl: m.liveUrl ?? '', techStack: (m.techStack ?? []).join(', '),
    startsAt: toLocal(m.startsAt), endsAt: toLocal(m.endsAt), onlineUrl: m.onlineUrl ?? '', registrationUrl: m.registrationUrl ?? '', codeLanguage: m.codeLanguage ?? '' };
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return <label className="block text-sm font-semibold">{label}<div className="mt-1 font-normal">{children}</div>{error && <p role="alert" className="mt-1 text-xs text-red-700">{error}</p>}</label>;
}

/** Create (no `post`) or edit (`post`, type is immutable) a community post. Server errors map back onto fields. */
export function PostComposer({ post, onSaved, onCancel }: { post?: Post; onSaved: (p: Post) => void; onCancel?: () => void }) {
  const editing = Boolean(post);
  const create = useCreatePost();
  const edit = useEditPost(post?.id ?? '');
  const [serverError, setServerError] = useState<string | null>(null);
  const [server, setServer] = useState<Record<string, string>>({});
  const { register, handleSubmit, watch, setValue, formState: { errors, isSubmitting } } = useForm<PostFormInput, unknown, PostFormOutput>({
    resolver: zodResolver(postFormSchema), defaultValues: initialValues(post),
  });
  const type = watch('type');
  const body = watch('body');
  const msg = (k: keyof PostFormInput) => (errors[k]?.message as string | undefined) ?? server[k] ?? server[`typeMetadata.${k}`];

  const submit = handleSubmit(async (v) => {
    setServerError(null); setServer({});
    try {
      const saved = editing
        ? await edit.mutateAsync({ title: v.title, body: v.body, tags: parseTags(v.tags), typeMetadata: metadataFor(v) })
        : await create.mutateAsync(toCreateRequest(v));
      onSaved(saved);
    } catch (e) {
      setServer(fieldErrors(e));
      setServerError(errorMessage(e, 'Could not save the post. Please try again.'));
    }
  });

  return (
    <Card>
      <form onSubmit={submit} className="space-y-4" noValidate aria-label={editing ? 'Edit post' : 'New post'}>
        <Field label="Post type" error={msg('type')}>
          <Select {...register('type')} disabled={editing}>{POST_TYPES.map((t) => <option key={t} value={t}>{POST_TYPE_LABEL[t]}</option>)}</Select>
        </Field>
        <Field label="Title (optional)" error={msg('title')}><Input maxLength={TITLE_MAX} {...register('title')} /></Field>
        <div>
          <MentionInput label="Your post" rows={6} value={body} onChange={(v) => setValue('body', v, { shouldValidate: true })} maxLength={BODY_MAX} />
          <p className="text-right text-xs text-slate-500">{body.length}/{BODY_MAX}</p>
          {msg('body') && <p role="alert" className="text-xs text-red-700">{msg('body')}</p>}
        </div>
        <Field label="Tags (up to 5, space or comma separated)" error={msg('tags')}><Input placeholder="java spring-boot" {...register('tags')} /></Field>

        {(type === 'HIRING' || type === 'REFERRAL') && <>
          <Field label="Company" error={msg('companyName')}><Input {...register('companyName')} /></Field>
          <Field label="Role" error={msg('roleTitle')}><Input {...register('roleTitle')} /></Field>
        </>}
        {type === 'HIRING' && <>
          <Field label="Location" error={msg('location')}><Input {...register('location')} /></Field>
          <Field label="Apply link (https)" error={msg('applyUrl')}><Input type="url" {...register('applyUrl')} /></Field>
        </>}
        {type === 'REFERRAL' && <Field label="Referral details" error={msg('referralDetails')}><Input {...register('referralDetails')} /></Field>}
        {type === 'PROJECT' && <>
          <Field label="Repository (https)" error={msg('repoUrl')}><Input type="url" {...register('repoUrl')} /></Field>
          <Field label="Live demo (https)" error={msg('liveUrl')}><Input type="url" {...register('liveUrl')} /></Field>
          <Field label="Tech stack (comma separated)" error={msg('techStack')}><Input {...register('techStack')} /></Field>
        </>}
        {type === 'TECHNICAL' && <Field label="Code language" error={msg('codeLanguage')}><Input {...register('codeLanguage')} /></Field>}
        {type === 'EVENT' && <>
          <Field label="Starts" error={msg('startsAt')}><Input type="datetime-local" {...register('startsAt')} /></Field>
          <Field label="Ends" error={msg('endsAt')}><Input type="datetime-local" {...register('endsAt')} /></Field>
          <Field label="Location" error={msg('location')}><Input {...register('location')} /></Field>
          <Field label="Online link (https)" error={msg('onlineUrl')}><Input type="url" {...register('onlineUrl')} /></Field>
          <Field label="Registration link (https)" error={msg('registrationUrl')}><Input type="url" {...register('registrationUrl')} /></Field>
        </>}

        {serverError && <p role="alert" className="text-sm text-red-700">{serverError}</p>}
        <div className="flex gap-3">
          <Button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Saving…' : editing ? 'Save changes' : 'Publish post'}</Button>
          {onCancel && <button type="button" onClick={onCancel} className="text-sm font-semibold underline">Cancel</button>}
        </div>
      </form>
    </Card>
  );
}
