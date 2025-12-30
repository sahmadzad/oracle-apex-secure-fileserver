declare
  l_id number;
begin
If validate_session(apex_application.g_x04,apex_application.g_x05)=1 Then
  insert into emp (
      ename,
      hiredate,
      sal,
      filename
  ) values (
      apex_application.g_x01,
      apex_application.g_x02,
      apex_application.g_x03,
      apex_application.g_x06
  )
  returning empno into l_id;

  commit;

  apex_json.open_object;
  apex_json.write('status', 'SUCCESS');
  apex_json.write('id', l_id);
  apex_json.close_object;
Else  
    apex_json.open_object;
    apex_json.write('status', 'ERROR');
    apex_json.write('message', 'Invalid Request');
    apex_json.close_object;  
End If;
exception
  when others then
    apex_json.open_object;
    apex_json.write('status', 'ERROR');
    apex_json.write('message', sqlerrm);
    apex_json.close_object;
end;

