package com.ticketbox.api.module.notification.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.notification.domain.entities.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

}





