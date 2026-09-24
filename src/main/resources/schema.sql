create table if not exists region(name varchar(90) primary key, isb bigint not null check(isb>0));
create table if not exists kitchen(id varchar(50) primary key,name varchar(120) not null,region varchar(90) references region(name));
create table if not exists account(id varchar(80) primary key,name varchar(120) not null,role varchar(12) not null,kitchen varchar(50) references kitchen(id),regions varchar(500) not null,hash varchar(100) not null,active boolean not null);
