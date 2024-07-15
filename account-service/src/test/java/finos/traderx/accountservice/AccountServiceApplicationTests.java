package finos.traderx.accountservice;

import finos.traderx.accountservice.model.Account;
import finos.traderx.accountservice.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(locations = "/test-application.properties")
class AccountServiceApplicationTests {

    @Autowired
    AccountService accountService;

    @Test
    void contextLoads() {
    }

    @Test
    void createAccount() {
        Account input_account = new Account();
        input_account.setDisplayName("test account");
        Account created_account = accountService.upsertAccount(input_account);

        Account output_account = accountService.getAccountById(created_account.getId());
        assertEquals(output_account.getDisplayName(), "test account");
    }    
}
