alter table reserva add column pagamento_metodo varchar(10);
alter table reserva add column pagamento_valor numeric(10, 2);
alter table reserva add column pagamento_valor_estornado numeric(10, 2);
alter table reserva add column pagamento_final_cartao varchar(4);
alter table reserva add column pagamento_transacao varchar(50);
