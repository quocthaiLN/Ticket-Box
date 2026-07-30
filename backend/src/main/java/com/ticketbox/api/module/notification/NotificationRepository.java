package com.ticketbox.api.module.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketbox.api.module.notification.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

}





