package org.dhole.internal.di;

/**
 * Small plain Java classes used as components in tests.
 */
final class Fixtures {

    private Fixtures() {
    }

    public static final class UserRepository {
    }

    public static final class UserService {

        final UserRepository users;

        public UserService(UserRepository users) {
            this.users = users;
        }
    }

    public static final class Mail {
    }

    public static final class UserController {

        final UserService users;
        final Mail mail;

        public UserController(UserService users, Mail mail) {
            this.users = users;
            this.mail = mail;
        }
    }

    public interface PaymentGateway {
    }

    public static final class StripePaymentGateway implements PaymentGateway {
    }

    public static final class PaypalPaymentGateway implements PaymentGateway {
    }

    public static final class OrderService {

        final PaymentGateway payments;

        public OrderService(PaymentGateway payments) {
            this.payments = payments;
        }
    }

    public abstract static class AbstractStorage {
    }

    public static final class TwoConstructors {

        public TwoConstructors() {
        }

        public TwoConstructors(UserRepository users) {
        }
    }

    public static final class PrivateConstructorOnly {

        private PrivateConstructorOnly() {
        }
    }

    public static final class PublicAndPrivateConstructors {

        final UserRepository users;

        public PublicAndPrivateConstructors(UserRepository users) {
            this.users = users;
        }

        private PublicAndPrivateConstructors() {
            this.users = null;
        }
    }

    static final class PackagePrivateClass {

        public PackagePrivateClass() {
        }
    }

    public final class InnerClass {
    }

    public static final class NeedsList {

        public NeedsList(java.util.List<UserRepository> repositories) {
        }
    }

    public static final class NeedsPrimitive {

        public NeedsPrimitive(int value) {
        }
    }

    public static final class CycleA {

        public CycleA(CycleB b) {
        }
    }

    public static final class CycleB {

        public CycleB(CycleC c) {
        }
    }

    public static final class CycleC {

        public CycleC(CycleA a) {
        }
    }

    public static final class SelfDependent {

        public SelfDependent(SelfDependent self) {
        }
    }

    public static final class LoopingGateway implements PaymentGateway {

        public LoopingGateway(OrderService orders) {
        }
    }

    public static final class NeedsMissing {

        public NeedsMissing(UserService users, AbstractStorage storage) {
        }
    }
}
