package com.eshoppingzone.order.returns.service;

import com.eshoppingzone.order.returns.dto.CreateReturnRequest;
import com.eshoppingzone.order.returns.dto.RejectReturnRequest;
import com.eshoppingzone.order.returns.dto.ReturnDto;
import com.eshoppingzone.order.returns.dto.UpdateReturnStatusRequest;
import com.eshoppingzone.order.returns.enums.ReturnStatus;

import java.util.List;

public interface ReturnService {

    ReturnDto createReturn(Long customerId, CreateReturnRequest request, String idempotencyKey);

    List<ReturnDto> getCustomerReturns(Long customerId);

    ReturnDto getCustomerReturnById(Long returnId, Long customerId);

    ReturnDto cancelReturn(Long returnId, Long customerId);

    List<ReturnDto> getAllReturns(ReturnStatus status);

    List<ReturnDto> getPendingReturns();

    ReturnDto getReturnByIdAdmin(Long returnId);

    ReturnDto approveReturn(Long returnId);

    ReturnDto rejectReturn(Long returnId, RejectReturnRequest request);

    ReturnDto updateReturnStatus(Long returnId, UpdateReturnStatusRequest request);

    ReturnDto retryRestock(Long returnId);

    ReturnDto retryRefund(Long returnId);
}
