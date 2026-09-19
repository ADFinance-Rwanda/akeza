package com.tenant.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPoint implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String date;
    private long count;
}
