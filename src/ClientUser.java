import java.util.Objects;

public class ClientUser {
    private String account;
    private String password;

    public ClientUser(String account, String password) {
        this.account = account;
        this.password = password;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientUser user = (ClientUser) o;
        return account.equals(user.account) && password.equals(user.password);
    }

    public int hashCode() {
        return Objects.hash(account, password);
    }
}
