package com.supportticketmanagement.repository;

import java.util.List;

import com.supportticketmanagement.persistence.Comment;
import com.supportticketmanagement.persistence.Ticket;
import org.springframework.data.repository.ListCrudRepository;

public interface CommentRepository extends ListCrudRepository<Comment, Long> {

    List<Comment> findAllByTicket(Ticket ticket);
}
