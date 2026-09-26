package com.acme.fixture.users;

public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }
}
