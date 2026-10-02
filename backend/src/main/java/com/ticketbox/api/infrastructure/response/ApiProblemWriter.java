package com.ticketbox.api.infrastructure.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public final class ApiProblemWriter {
    private ApiProblemWriter() {}

    public static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                             int status, String code, String detail) throws IOException {
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        var problem = ApiProblemFactory.create(httpStatus, code, detail, java.util.Map.of(), request);
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", problem.getInstance());
        body.putAll(problem.getProperties());
        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), body);
    }
}
