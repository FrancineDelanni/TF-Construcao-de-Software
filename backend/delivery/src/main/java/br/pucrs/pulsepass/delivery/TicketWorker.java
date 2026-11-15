package br.pucrs.pulsepass.delivery;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="pulsepass.delivery.worker.enabled",havingValue="true",matchIfMissing=true)
public class TicketWorker {
 private final TicketJobs jobs;private final TicketPdf pdf;
 public TicketWorker(TicketJobs jobs,TicketPdf pdf) {this.jobs=jobs;this.pdf=pdf;}
 @Scheduled(fixedDelayString="${pulsepass.delivery.worker.delay:1000}")
 public void run() {
  for(int i=0;i<10;i++) {
   var candidate=jobs.claim();if(candidate.isEmpty()) return;var t=candidate.get();
   var id=(UUID)t.get("id");var lease=(UUID)t.get("lease_token");
   try {jobs.complete(id,lease,pdf.render(t));}
   catch(Exception ex) {jobs.failed(id,lease);LoggerFactory.getLogger(TicketWorker.class).warn("PDF pendente para ingresso {}; nova tentativa agendada",id);}
  }
 }
}
