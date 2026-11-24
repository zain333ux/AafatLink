-- Reliable UC01 evidence staging and uncertain-result reconciliation.
create function public.begin_request_operation(p_operation_id uuid, p_operation_type text)
returns void language plpgsql security invoker set search_path = '' as $$
declare
  actor_id uuid := auth.uid();
  existing public.workflow_operations%rowtype;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  if p_operation_type not in ('SUBMIT_REQUEST', 'RESUBMIT_REQUEST') then
    raise exception 'Unsupported request operation type' using errcode = '22023';
  end if;
  select * into existing from public.workflow_operations where operation_id = p_operation_id;
  if found then
    if existing.actor_account_id <> actor_id or existing.operation_type <> p_operation_type then
      raise exception 'Operation ID belongs to a different operation' using errcode = '42501';
    end if;
    return;
  end if;
  insert into public.workflow_operations(operation_id, actor_account_id, operation_type)
  values (p_operation_id, actor_id, p_operation_type);
end;
$$;

create function public.register_staged_request_evidence(
  p_evidence_id uuid, p_operation_id uuid, p_object_path text,
  p_original_filename text, p_content_type text, p_size_bytes bigint
)
returns void language plpgsql security invoker set search_path = '' as $$
declare
  actor_id uuid := auth.uid();
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  if p_object_path not like actor_id::text || '/' || p_operation_id::text || '/%' then
    raise exception 'Evidence object path does not belong to this operation' using errcode = '42501';
  end if;
  if p_content_type not in ('image/jpeg', 'image/png', 'application/pdf') then
    raise exception 'Unsupported evidence content type' using errcode = '22023';
  end if;
  insert into public.request_evidence(
    id, operation_id, uploaded_by_account_id, object_path,
    original_filename, content_type, size_bytes, state
  ) values (
    p_evidence_id, p_operation_id, actor_id, p_object_path,
    btrim(p_original_filename), p_content_type, p_size_bytes, 'STAGED'
  );
end;
$$;

-- FOR UPDATE waits behind a still-running submission transaction before reporting state.
create function public.reconcile_request_operation(p_operation_id uuid)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if not found then return jsonb_build_object('status', 'MISSING'); end if;
  if operation.actor_account_id <> actor_id then
    raise exception 'Operation is unavailable' using errcode = '42501';
  end if;
  return jsonb_build_object('status', operation.status, 'result', operation.result);
end;
$$;

-- Definer rights are limited to abandoning the authenticated actor's uncommitted operation.
create function public.abandon_request_operation(p_operation_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  select * into operation from public.workflow_operations
  where operation_id = p_operation_id for update;
  if not found or operation.actor_account_id <> actor_id then
    raise exception 'Operation is unavailable' using errcode = '42501';
  end if;
  if operation.operation_type not in ('SUBMIT_REQUEST', 'RESUBMIT_REQUEST') then
    raise exception 'Operation cannot be abandoned by this workflow' using errcode = '42501';
  end if;
  if operation.status <> 'PENDING' then return false; end if;
  if exists (select 1 from public.assistance_requests where operation_id = p_operation_id) then
    return false;
  end if;
  update public.request_evidence set state = 'ORPHAN_CANDIDATE'
  where operation_id = p_operation_id and state = 'STAGED';
  update public.workflow_operations
  set status = 'FAILED', failure_code = 'CLIENT_ABANDONED', completed_at = now()
  where operation_id = p_operation_id;
  return true;
end;
$$;

revoke all on function public.begin_request_operation(uuid, text) from public, anon;
revoke all on function public.register_staged_request_evidence(uuid, uuid, text, text, text, bigint) from public, anon;
revoke all on function public.reconcile_request_operation(uuid) from public, anon;
revoke all on function public.abandon_request_operation(uuid) from public, anon;
grant execute on function public.begin_request_operation(uuid, text) to authenticated;
grant execute on function public.register_staged_request_evidence(uuid, uuid, text, text, text, bigint) to authenticated;
grant execute on function public.reconcile_request_operation(uuid) to authenticated;
grant execute on function public.abandon_request_operation(uuid) to authenticated;
