-- Reuse the reliable staged-evidence lifecycle for Coordinator-entered UC01 requests.

create or replace function public.begin_request_operation(p_operation_id uuid, p_operation_type text)
returns void language plpgsql security invoker set search_path = '' as $$
declare
  actor_id uuid := auth.uid();
  existing public.workflow_operations%rowtype;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  if p_operation_type not in ('SUBMIT_REQUEST', 'RESUBMIT_REQUEST', 'RECORD_COORDINATOR_REQUEST') then
    raise exception 'Unsupported request operation type' using errcode = '22023';
  end if;
  if p_operation_type = 'RECORD_COORDINATOR_REQUEST'
      and private.current_app_role() <> 'COORDINATOR' then
    raise exception 'Coordinator permission required' using errcode = '42501';
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

create or replace function public.abandon_request_operation(p_operation_id uuid)
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
  if operation.operation_type not in (
      'SUBMIT_REQUEST', 'RESUBMIT_REQUEST', 'RECORD_COORDINATOR_REQUEST') then
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
revoke all on function public.abandon_request_operation(uuid) from public, anon;
grant execute on function public.begin_request_operation(uuid, text) to authenticated;
grant execute on function public.abandon_request_operation(uuid) to authenticated;
