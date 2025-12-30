DECLARE
  v_return NUMBER;
BEGIN
  v_return := schema_name.VALIDATE_SESSION(
                P_APP_ID     => :app_id,
                P_SESSION_ID => :session_id
              );

  IF v_return = 1 THEN
    :status   := 200;
    sys.htp.p('{"is_valid":"1"}');
  ELSE
    :status   := 401;
     sys.htp.p('{"is_valid":"0"}');
  END IF;
END;


curl -X GET "http://your_ords_apex_server:port/ords/validate_session/result?p_app_id=128&p_session_id=6003772635841"
