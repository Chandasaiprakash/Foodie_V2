package com.foodie.order_service.service;

import com.foodie.common.correlation.CorrelationContext;
import com.foodie.order_service.model.Order;
import com.foodie.common.events.OrderCreatedEvent;
import com.foodie.order_service.model.OrderItem;
import com.foodie.order_service.outbox.OutboxEventService;
import com.foodie.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventService outboxEventService;

    private static final String TOPIC = "order-created";

    @Transactional
    public Order createOrder(Order orderRequest, String customerEmail) {
        Order order = new Order();
        order.setCustomerEmail(customerEmail);
        order.setOrderUuid(UUID.randomUUID().toString());
        order.setStatus("CREATED");
        order.setPaymentStatus("PENDING");
        order.setCreatedAt(Instant.now());
        order.setRestaurantId(orderRequest.getRestaurantId());
        order.setRestaurantName(orderRequest.getRestaurantName());
        order.setCustomerPhone(orderRequest.getCustomerPhone());
        order.setItems(orderRequest.getItems());

        double total = orderRequest.getItems().stream()
                .mapToDouble(item -> item.getPrice() * (item.getQuantity() == null ? 1 : item.getQuantity()))
                .sum();
        order.setTotal(total);

        Order saved = orderRepository.save(order);

        // Capture correlationId from MDC (set by CorrelationIdFilter) and embed in event
        // so every downstream service and Kafka consumer can restore it.
        String correlationId = CorrelationContext.get();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderUuid(saved.getOrderUuid())
                .customerEmail(saved.getCustomerEmail())
                .customerPhone(saved.getCustomerPhone())
                .restaurantId(saved.getRestaurantId())
                .restaurantName(saved.getRestaurantName())
                .items(saved.getItems().stream()
                        .map(i -> new OrderCreatedEvent.OrderItemDto(i.getName(), i.getQuantity(), i.getPrice()))
                        .collect(Collectors.toList()))
                .total(saved.getTotal())
                .correlationId(correlationId)
                .build();

        outboxEventService.save(TOPIC, saved.getOrderUuid(), event);

        log.info("Order created and outbox event queued: orderUuid={}", saved.getOrderUuid());
        return saved;
    }

    public Optional<Order> getById(Long id) {
        return orderRepository.findById(id);
    }

    public void updatePaymentStatus(String orderUuid, String status) {
        Optional<Order> optionalOrder = orderRepository.findByOrderUuid(orderUuid);
        if (optionalOrder.isEmpty()) return;

        Order order = optionalOrder.get();
        order.setPaymentStatus(status);
        if (status.equals("FAILED")) {
            order.setStatus("PAYMENT_FAILED");
        }
        orderRepository.save(order);
        log.info("Order {} marked as {}", orderUuid, status);
    }

    public Optional<Order> getByUuid(String uuid) {
        return orderRepository.findByOrderUuid(uuid);
    }

    public List<Order> getByCustomerEmail(String customerEmail) {
        return orderRepository.findByCustomerEmail(customerEmail);
    }

    @Transactional
    public boolean deleteOrder(String orderUuid) {
        Optional<Order> order = orderRepository.findByOrderUuid(orderUuid);
        if (order.isPresent()) {
            orderRepository.delete(order.get());
            return true;
        }
        return false;
    }

    public Optional<Order> getByIdIfOwned(Long id, String email) {
        return getById(id).filter(order -> order.getCustomerEmail().equalsIgnoreCase(email));
    }

    public Optional<Order> getByUuidIfOwned(String uuid, String email) {
        return getByUuid(uuid).filter(order -> order.getCustomerEmail().equalsIgnoreCase(email));
    }

    public Map<String, Object> getPagedOrders(String email, int page, int size,
                                               String sort, String search) {
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<Order> orderPage = (search != null && !search.isBlank())
                ? orderRepository.findByCustomerEmailAndRestaurantNameContainingIgnoreCase(email, search, pageable)
                : orderRepository.findByCustomerEmail(email, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("orders", orderPage.getContent());
        response.put("currentPage", orderPage.getNumber());
        response.put("totalItems", orderPage.getTotalElements());
        response.put("totalPages", orderPage.getTotalPages());
        return response;
    }
}
