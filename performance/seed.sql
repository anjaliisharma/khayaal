-- Synthetic performance data only. Run against a disposable database.
-- Requires psql variable user_id for an existing synthetic account: psql -v user_id="'uuid'" -f performance/seed.sql
INSERT INTO households(id,name,default_timezone,created_at)
VALUES ('00000000-0000-0000-0000-000000000001','Performance household','UTC',now()) ON CONFLICT DO NOTHING;
INSERT INTO household_memberships(id,household_id,user_id,role)
VALUES (gen_random_uuid(),'00000000-0000-0000-0000-000000000001',:user_id,'OWNER') ON CONFLICT (household_id,user_id) DO NOTHING;
INSERT INTO care_recipients(id,household_id,name,timezone,archived)
SELECT ('10000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,'00000000-0000-0000-0000-000000000001','Synthetic person '||n,'UTC',false FROM generate_series(1,20)n ON CONFLICT DO NOTHING;
INSERT INTO medicines(id,recipient_id,name,form,units_per_dose,start_date,as_needed,active,lead_time_days)
SELECT ('20000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,('10000000-0000-0000-0000-'||lpad((((n-1)%20)+1)::text,12,'0'))::uuid,'Synthetic medicine '||n,'tablet',1,current_date,false,true,3 FROM generate_series(1,150)n ON CONFLICT DO NOTHING;
INSERT INTO dose_events(id,medicine_id,scheduled_at,scheduled_local,status)
SELECT gen_random_uuid(),('20000000-0000-0000-0000-'||lpad((((n-1)%150)+1)::text,12,'0'))::uuid,now()+(n||' minutes')::interval,(now()+(n||' minutes')::interval)::timestamp,'PENDING' FROM generate_series(1,100000)n ON CONFLICT DO NOTHING;
INSERT INTO notifications(id,user_id,household_id,channel,notification_type,priority,subject,body,dedupe_key,status,attempts,next_attempt_at,created_at)
SELECT gen_random_uuid(),:user_id,'00000000-0000-0000-0000-000000000001','EMAIL','TEST',
       CASE WHEN n % 20 = 0 THEN 'HIGH' ELSE 'NORMAL' END,'Synthetic notification','Synthetic load-test message',
       'PERFORMANCE:'||n,'PENDING',0,now(),now()
FROM generate_series(1,10000)n ON CONFLICT DO NOTHING;
