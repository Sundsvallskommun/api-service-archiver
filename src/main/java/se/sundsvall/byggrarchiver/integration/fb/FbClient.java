package se.sundsvall.byggrarchiver.integration.fb;

import generated.sokigo.fb.ResponseDtoIEnumerableFastighetDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.byggrarchiver.integration.fb.configuration.FbConfiguration;

import java.util.List;

@FeignClient(name = "fb", url = "${integration.fb.url}", configuration = FbConfiguration.class)
@CircuitBreaker(name = "fb")
public interface FbClient {

    @Retry(name = "FbClient")
    @PostMapping(path = "Fastighet/info/fnr", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseDtoIEnumerableFastighetDto getPropertyInfoByFnr(@RequestBody List<Integer> fnrList, @RequestParam("Database") String database,
                                                            @RequestParam("User") String user, @RequestParam("Password") String password);

}
