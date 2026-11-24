-- Run as the database owner on a disposable development database after migrations.
-- Transaction-scoped synthetic actors exercise the application RPCs under the authenticated role.
begin;
create temporary table qa_users(role public.app_role primary key, id uuid default gen_random_uuid()) on commit drop;
insert into qa_users(role) values ('REQUESTER'), ('VOLUNTEER'), ('COORDINATOR'), ('ADMINISTRATOR');
insert into auth.users(id,email,raw_user_meta_data,email_confirmed_at)
select id, id::text || '@example.invalid', jsonb_build_object('display_name','Workflow test','account_type',role),now()
from qa_users;
do $$
begin
 if exists(select from qa_users u join public.accounts a using(id)
   where a.role <> case when u.role='VOLUNTEER' then 'VOLUNTEER'::public.app_role else 'REQUESTER' end)
   or not exists(select from public.volunteer_profiles v join qa_users u on u.id=v.account_id
     where u.role='VOLUNTEER' and v.approval_status='PENDING') then
   raise exception 'Incorrect account onboarding';
 end if;
end $$;
update public.accounts a set role=u.role from qa_users u where a.id=u.id and u.role in ('COORDINATOR','ADMINISTRATOR');
grant select on qa_users to authenticated;
set local role authenticated;
do $$
declare
 requester uuid := (select id from qa_users where role='REQUESTER');
 coordinator uuid := (select id from qa_users where role='COORDINATOR');
 volunteer uuid := (select id from qa_users where role='VOLUNTEER');
 administrator uuid := (select id from qa_users where role='ADMINISTRATOR');
 request_id uuid;
 op uuid := gen_random_uuid();
 result jsonb;
begin
 perform set_config('request.jwt.claim.sub',requester::text,true);
 perform public.begin_request_operation(op,'SUBMIT_REQUEST');
 perform public.register_staged_request_evidence(gen_random_uuid(),op,requester::text || '/' || op::text || '/sample.pdf','sample.pdf','application/pdf',256);
 result := public.submit_assistance_request(op,requester,'flood',null,'[TEST] River Road',
   '[TEST] Flooded road','Water supplies','Four residents');
 request_id := (result->>'request_id')::uuid;
 if public.reconcile_request_operation(op)->>'status' is distinct from 'COMMITTED'
    or public.abandon_request_operation(op) or (result->>'evidence_count')::integer <> 1 then
   raise exception 'Committed evidence must survive uncertain submission and cleanup';
 end if;
 if public.submit_assistance_request(op,requester,'flood',null,'[TEST] River Road',
   '[TEST] Flooded road','Water supplies','Four residents') is distinct from result then
   raise exception 'Retry did not recover the original request';
 end if;
 perform set_config('request.jwt.claim.sub',coordinator::text,true);
 if public.get_request_review_details(request_id)->>'requester_contact' is distinct from (requester::text || '@example.invalid') then
   raise exception 'Email-only contact snapshot missing';
 end if;
 perform public.decide_request_verification(gen_random_uuid(),request_id,0,'VERIFIED',null);
 perform public.decide_request_verification(gen_random_uuid(),request_id,1,'ADDITIONAL_INFORMATION_REQUIRED','Confirm affected count');
 perform set_config('request.jwt.claim.sub',requester::text,true);
 result := public.resubmit_assistance_request(gen_random_uuid(),request_id,2,'flood',null,'[TEST] River Road',
    '[TEST] Flooded road','Water supplies','Six residents');
 if (result->>'request_id')::uuid is distinct from request_id or result->>'status' <> 'PENDING_VERIFICATION' then
   raise exception 'Resubmission lost request identity or state';
 end if;
 perform set_config('request.jwt.claim.sub',volunteer::text,true);
 if public.reconcile_workflow_operation(op)->>'status' is distinct from 'MISSING' then
   raise exception 'Operation results must be scoped to the submitting actor';
 end if;
 if has_schema_privilege(current_user,'private','USAGE') and has_table_privilege(current_user,'private.request_contacts','SELECT') then
   raise exception 'Volunteer has private contact access';
 end if;
 begin
   perform public.get_request_review_details(request_id);
   raise exception 'Volunteer could read coordinator review';
 exception when insufficient_privilege then null;
 end;
 perform set_config('request.jwt.claim.sub',administrator::text,true);
 perform public.update_user_access(gen_random_uuid(),volunteer,0,'VOLUNTEER','ACTIVE','APPROVED','[TEST] Approved');
 op := gen_random_uuid();
 result := public.save_controlled_reference_value(op,'VOLUNTEER_SKILL',null,'[TEST] ' || op,true,0,'[TEST] Add skill');
 if public.save_controlled_reference_value(op,'VOLUNTEER_SKILL',null,'[TEST] ' || op,true,0,'[TEST] Add skill')
     is distinct from result then raise exception 'Reference-data retry lost its result'; end if;
 if not exists(select from public.volunteer_profiles where account_id=volunteer and approval_status='APPROVED') then
   raise exception 'Volunteer approval missing';
 end if;
end $$;
rollback;
