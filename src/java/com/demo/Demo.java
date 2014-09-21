package com.demo;

import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool.Config;


public class Demo {
	
	public static void main(String args[]){
		
		Config config  = new Config();
		config.maxActive = 10;
		
		GenericObjectPool<PoolObject>  pool = new GenericObjectPool<PoolObject>(new MyPoolableObjectFactory(),config);
		
		try {
			PoolObject obj = pool.borrowObject();
			System.out.print(obj.hashCode());
			pool.returnObject(obj);
			obj = pool.borrowObject();
			System.out.print(obj.hashCode());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
	}

}



  class MyPoolableObjectFactory implements PoolableObjectFactory{
	  private int i = 0;

	@Override
	public void activateObject(Object obj) throws Exception {
		if(i != 0){
			throw new RuntimeException("haha--");
		}
		
		i = 7;
	}

	@Override
	public void destroyObject(Object obj) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Object makeObject() throws Exception {
		
		return new PoolObject();
	}

	@Override
	public void passivateObject(Object obj) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean validateObject(Object obj) {
		// TODO Auto-generated method stub
		return false;
	}
	 
	 
	 
	 
 }
