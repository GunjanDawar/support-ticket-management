package com.supportticketmanagement.repository;

import java.util.List;

import com.supportticketmanagement.persistence.Ticket;
import com.supportticketmanagement.persistence.TicketStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface TicketRepository extends ListCrudRepository<Ticket, Long> {

    @Query("""
            select ticket
            from Ticket ticket
            where ?1 = ''
               or lower(ticket.title) like lower(concat('%', ?#{escape([0])}, '%'))
                    escape ?#{escapeCharacter()}
               or lower(ticket.description) like lower(concat('%', ?#{escape([0])}, '%'))
                    escape ?#{escapeCharacter()}
            """)
    List<Ticket> searchByKeyword(String keyword);

    List<Ticket> findAllByStatus(TicketStatus status);

    @Query("""
            select ticket
            from Ticket ticket
            where (
                    ?1 = ''
                    or lower(ticket.title) like lower(concat('%', ?#{escape([0])}, '%'))
                        escape ?#{escapeCharacter()}
                    or lower(ticket.description) like lower(concat('%', ?#{escape([0])}, '%'))
                        escape ?#{escapeCharacter()}
                  )
              and ticket.status = ?2
            """)
    List<Ticket> searchByKeywordAndStatus(String keyword, TicketStatus status);
}
