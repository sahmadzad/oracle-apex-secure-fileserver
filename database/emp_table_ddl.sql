  CREATE TABLE "EDU"."EMP" 
   (	"EMPNO" NUMBER(4,0) NOT NULL ENABLE, 
	"ENAME" VARCHAR2(100 BYTE), 
	"JOB" VARCHAR2(9 BYTE), 
	"MGR" NUMBER(4,0), 
	"HIREDATE" DATE, 
	"SAL" NUMBER(7,2), 
	"COMM" NUMBER(7,2), 
	"DEPTNO" NUMBER(2,0), 
	"MIME_TYPE" VARCHAR2(200 CHAR), 
	"EMP_DOC" BLOB, 
	"FILENAME" VARCHAR2(200 BYTE), 
	 PRIMARY KEY ("EMPNO")
   ) ;

  CREATE OR REPLACE EDITIONABLE TRIGGER "EDU"."EMP_TRG1" 
              before insert on emp
              for each row
              begin
                  if :new.empno is null then
                      select emp_seq.nextval into :new.empno from sys.dual;
                 end if;
              end;
/
ALTER TRIGGER "EDU"."EMP_TRG1" ENABLE;

CREATE SEQUENCE emp_seq;
