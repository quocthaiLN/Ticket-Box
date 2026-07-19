package com.ticketbox.api.notification.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.notification.entities.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

}





