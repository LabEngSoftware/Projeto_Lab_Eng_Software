alter table reserva add column validado_em timestamp with time zone;
alter table reserva add column quantidade_entregue numeric(10, 2);
alter table reserva add column atrasada boolean not null default false;
alter table reserva add column motivo_cancelamento varchar(500);
