-- Evidence objects are private. Paths follow <actor-uuid>/<operation-uuid>/<filename>.
insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
values (
  'request-evidence',
  'request-evidence',
  false,
  10485760,
  array['image/jpeg', 'image/png', 'application/pdf']
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy evidence_objects_insert_own_staging
on storage.objects for insert to authenticated
with check (
  bucket_id = 'request-evidence'
  and (storage.foldername(name))[1] = (select auth.uid())::text
  and (storage.foldername(name))[2] is not null
);

create policy evidence_objects_select_authorized
on storage.objects for select to authenticated
using (
  bucket_id = 'request-evidence'
  and (
    owner_id = (select auth.uid())::text
    or exists (
      select 1
      from public.request_evidence evidence
      where evidence.bucket_id = storage.objects.bucket_id
        and evidence.object_path = storage.objects.name
        and evidence.state = 'COMMITTED'
    )
  )
);

-- Cleanup is deliberately restricted to the uploader and is impossible after a
-- committed database row references the path, including after an uncertain RPC result.
create policy evidence_objects_delete_uncommitted_own
on storage.objects for delete to authenticated
using (
  bucket_id = 'request-evidence'
  and owner_id = (select auth.uid())::text
  and not exists (
    select 1
    from public.request_evidence evidence
    where evidence.bucket_id = storage.objects.bucket_id
      and evidence.object_path = storage.objects.name
      and evidence.state = 'COMMITTED'
  )
);
