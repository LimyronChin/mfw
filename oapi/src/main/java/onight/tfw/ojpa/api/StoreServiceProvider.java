package onight.tfw.ojpa.api;


public abstract interface StoreServiceProvider {
	public abstract String getProviderid();

	public abstract String[] getContextConfigs();

	public abstract DomainDaoSupport getDaoByBeanName(OJpaDAO oJdao);
}
