-- Lock an operation without granting desktop clients UPDATE on its table.
create or replace function public.reconcile_workflow_operation(p_operation_id uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  actor_id uuid := auth.uid();
  operation public.workflow_operations%rowtype;
begin
  if actor_id is null then raise exception 'Authentication required' using errcode = '28000'; end if;
  select * into operation from public.workflow_operations
  where operation_id = p_operation_id and actor_account_id = actor_id for update;
  if not found then return jsonb_build_object('status','MISSING'); end if;
  return jsonb_build_object('status',operation.status,'result',operation.result);
end;
$$;
revoke all on function public.reconcile_workflow_operation(uuid) from public, anon;
grant execute on function public.reconcile_workflow_operation(uuid) to authenticated;

create or replace function public.reconcile_request_operation(p_operation_id uuid)
returns jsonb language sql security invoker set search_path = '' as $$
  select public.reconcile_workflow_operation(p_operation_id)
$$;
revoke all on function public.reconcile_request_operation(uuid) from public, anon;
grant execute on function public.reconcile_request_operation(uuid) to authenticated;
