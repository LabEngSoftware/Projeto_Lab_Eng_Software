package resgatai.notificacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import resgatai.usuario.Usuario;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * RF07: grava a notificação na caixa de entrada, dentro da transação do evento (só existe se o evento existir),
 * e um worker a envia por e-mail depois. SMTP fora do ar não derruba reserva nem cadastro: o e-mail fica na fila.
 * Sem spring.mail.host configurado, só a caixa de entrada funciona.
 * ponytail: o "push" é o PWA consultando GET /notificacoes; Web Push (VAPID) quando precisar notificar com o app fechado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Notificador {

    /** Números com vírgula decimal nas mensagens, independente do locale do servidor. */
    public static final Locale PT_BR = Locale.of("pt", "BR");

    /** Data e hora nas mensagens ao usuário, no fuso de São Paulo. */
    public static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final NotificacaoRepository notificacoes;
    private final ObjectProvider<JavaMailSender> mail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.remetente}")
    private String remetente;

    public void notificar(Usuario destino, String titulo, String mensagem, String link) {
        notificacoes.save(new Notificacao(destino, titulo, mensagem, link));
    }

    /**
     * Envio imediato, fora da fila e da caixa de entrada, para conteúdo sensível (link de primeiro acesso).
     * Retorna false se não há SMTP configurado ou o envio falhou, para quem chamou oferecer outro caminho.
     */
    public boolean enviarEmail(Usuario destino, String assunto, String texto) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) return false;
        try {
            sender.send(email(destino, assunto, texto));
            return true;
        } catch (MailException e) {
            log.warn("Falha ao enviar e-mail para {}: {}", destino.getEmail(), e.getMessage());
            return false;
        }
    }

    /**
     * Esvazia a fila de e-mails; o que falhar fica para a próxima rodada, sem segurar os demais.
     * ponytail: e-mail recusado para sempre é retentado a cada rodada; contar tentativas se aparecer no log.
     */
    @Scheduled(cron = "${app.notificacao.cron}")
    @Transactional
    public void enviarEmails() {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) return;
        for (Notificacao n : notificacoes.findTop50ByEmailEnviadoEmIsNullOrderById()) {
            try {
                sender.send(email(n.getUsuario(), "ResgatAI: " + n.getTitulo(),
                        n.getMensagem() + (n.getLink() == null ? "" : "\n\n" + frontendUrl + n.getLink())));
                n.setEmailEnviadoEm(Instant.now());
            } catch (MailException e) {
                log.warn("E-mail da notificação {} segue na fila: {}", n.getId(), e.getMessage());
            }
        }
    }

    private SimpleMailMessage email(Usuario destino, String assunto, String texto) {
        var m = new SimpleMailMessage();
        m.setFrom(remetente);
        m.setTo(destino.getEmail());
        m.setSubject(assunto);
        m.setText(texto);
        return m;
    }
}
