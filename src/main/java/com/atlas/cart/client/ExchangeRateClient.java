package com.atlas.cart.client;

import com.atlas.cart.client.dto.ExchangeRateDto;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
    name = "hotel-service",
    url = "${clients.frankfurter.base-url}"
)
public interface ExchangeRateClient {

  @GetMapping("/v2/rates?base=USD")
  List<ExchangeRateDto> getUSDExchangeRates();
}