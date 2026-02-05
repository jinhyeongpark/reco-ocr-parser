package kr.co.reco.ocr.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WeightTicketRepository extends JpaRepository<WeightTicket, Long> {

    List<WeightTicket> findAllByNeedsReviewTrue();
}
