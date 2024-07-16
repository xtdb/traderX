package finos.traderx.accountservice.repository;

import finos.traderx.accountservice.model.Account;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Optional;

public interface AccountRepository extends Repository<Account, Integer> {
    Account save(Account account);

    Optional<Account> findById(int id);

    Iterable<Account> findAll(String asOf);

    class AccountRepositoryImpl implements AccountRepository {

        @Autowired
        private DataSource pool;

        @Override
        public Account save(Account account) {
            try (var conn = pool.getConnection();
                 var stmt = conn.prepareStatement("INSERT INTO account (_id, display_name) VALUES (?, ?)")) {
                stmt.setInt(1, account.getId());
                stmt.setObject(2, account.getDisplayName());
                stmt.execute();
                return account;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private Account fromResultSet(ResultSet rs) throws SQLException {
            var a = new Account();
            a.setId(rs.getInt("_id"));
            a.setDisplayName(rs.getString("display_name"));
            return a;
        }

        @Override
        public Optional<Account> findById(int id) {
            try (var conn = pool.getConnection();
                 var stmt = conn.prepareStatement("SELECT * FROM account WHERE _id = ?")) {
                stmt.setInt(1, id);
                try (var res = stmt.executeQuery()) {
                    if (res.next()) {
                        return Optional.of(fromResultSet(res));
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Iterable<Account> findAll(String asOf) {
            try (var conn = pool.getConnection()) {
                try (var stmt = conn.createStatement()) {
                        try (var res =
                                     switch (asOf) {
                                         case "NOW":
                                             yield stmt.executeQuery("SELECT _id, display_name FROM account FOR VALID_TIME AS OF NOW");
                                         case "ALL_TIME":
                                             yield stmt.executeQuery("SELECT _id, display_name FROM account FOR ALL VALID_TIME");
                                         default:
                                             yield stmt.executeQuery("SELECT _id, display_name FROM account FOR VALID_TIME AS OF TIMESTAMP '" + asOf + "'");
                                     })
                        {

                            var out = new LinkedList<Account>();
                            while (res.next()) {
                                out.add(fromResultSet(res));
                            }
                            return out;

                        }

                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
