-- Development SQL editor: replace the four reserved example addresses with verified demo accounts.
begin;
select pg_advisory_xact_lock(hashtext('aafatlink.demo.v1'));
create temporary table demo_actor(role public.app_role primary key, email text, id uuid) on commit drop;
insert into demo_actor(role,email) values
 ('REQUESTER','requester@example.invalid'), ('VOLUNTEER','volunteer@example.invalid'),
 ('COORDINATOR','coordinator@example.invalid'), ('ADMINISTRATOR','administrator@example.invalid');
update demo_actor d set id = a.id from public.accounts a join auth.users u on u.id=a.id
where lower(a.email)=lower(d.email) and a.role=d.role and a.status='ACTIVE' and u.email_confirmed_at is not null;

create or replace function pg_temp.demo_operation(actor uuid, action text, at_time timestamptz) returns uuid
language sql as $$
 insert into public.workflow_operations(operation_id,actor_account_id,operation_type,status,created_at,completed_at)
 values(gen_random_uuid(),actor,action,'COMMITTED',at_time,at_time) returning operation_id
$$;
do $$
declare
 requester uuid := (select id from demo_actor where role='REQUESTER');
 volunteer uuid := (select id from demo_actor where role='VOLUNTEER');
 coordinator uuid := (select id from demo_actor where role='COORDINATOR');
 req uuid;
 assignment uuid;
 submitted timestamptz;
 state public.request_status;
begin
 if exists(select from demo_actor where id is null) then
   raise exception 'Each configured account must be verified, active and have the specified role';
 end if;
 if not exists(select from public.volunteer_profiles where account_id=volunteer
    and approval_status='APPROVED' and availability_status='AVAILABLE') then
   raise exception 'Approve the demo volunteer and set readiness to Available';
 end if;
 for i in 1..7 loop
   req := md5('aafatlink-demo-v1:' || requester::text || ':' || i)::uuid;
   if exists(select from public.assistance_requests where id=req and requester_account_id=requester
      and location_address='[DEMO] Service area ' || i) then continue; end if;
   submitted := now() - make_interval(days=>i);
   state := (case when i=1 then 'PENDING_VERIFICATION' when i in (2,3) then 'VERIFIED'
                  when i=4 then 'AWAITING_CLOSURE_REVIEW' else 'CLOSED' end)::public.request_status;
   insert into public.assistance_requests(id,requester_account_id,recorded_by_account_id,operation_id,
    disaster_type,location_address,description,required_assistance,affected_person_details,status,priority,submitted_at,version,updated_at)
   values(req,requester,requester,pg_temp.demo_operation(requester,'SUBMIT_REQUEST',submitted),'flood',
     '[DEMO] Service area ' || i,'[DEMO] Flood relief exercise','[DEMO] Supply delivery',
     '[DEMO] Four residents',state,case when i>=3 then 'HIGH'::public.request_priority end,submitted,
     case when i<4 then i-1 when i=4 then 4 else 5 end,
     submitted + case i when 1 then interval '0' when 2 then interval '10 minutes' when 3 then interval '15 minutes'
       when 4 then interval '1 hour' else interval '2 hours' end);
   if i>=2 then
     insert into public.request_verifications(request_id,coordinator_account_id,decision,reviewed_request_version,created_at)
     values(req,coordinator,'VERIFIED',0,submitted+interval '10 minutes');
   end if;
   if i>=3 then
     insert into public.priority_assessments(request_id,coordinator_account_id,suggested_priority,selected_priority,reviewed_request_version,created_at)
     values(req,coordinator,'HIGH','HIGH',1,submitted+interval '15 minutes');
   end if;
   if i>=4 then
     insert into public.volunteer_assignments(request_id,volunteer_account_id,assigned_by_account_id,operation_id,status,assigned_at,version,updated_at)
     values(req,volunteer,coordinator,pg_temp.demo_operation(coordinator,'ASSIGN_VOLUNTEER',submitted+interval '20 minutes'),
       case when i=4 then 'AWAITING_CLOSURE_REVIEW'::public.assignment_status else 'COMPLETED' end,
       submitted+interval '20 minutes',case when i=4 then 2 else 3 end,
       submitted+case when i=4 then interval '1 hour' else interval '2 hours' end) returning id into assignment;
     insert into public.assignment_decisions(assignment_id,volunteer_account_id,accepted,created_at)
       values(assignment,volunteer,true,submitted+interval '25 minutes');
     insert into public.task_completions(assignment_id,volunteer_account_id,remarks,operation_id,completed_at)
     values(assignment,volunteer,'[DEMO] Supplies delivered',pg_temp.demo_operation(volunteer,'COMPLETE_TASK',submitted+interval '1 hour'),submitted+interval '1 hour');
     if i>=5 then
       insert into public.closure_reviews(request_id,assignment_id,coordinator_account_id,decision,operation_id,created_at)
       values(req,assignment,coordinator,'APPROVED',pg_temp.demo_operation(coordinator,'REVIEW_CLOSURE',submitted+interval '2 hours'),submitted+interval '2 hours');
     end if;
   end if;
 end loop;
end $$;
commit;
