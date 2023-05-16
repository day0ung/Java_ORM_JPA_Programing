# JPA

## 하이버네이트 (Hibernate)
ORM Framework 중 하나. 'JPA 프로바이더' 라고도 부름,  
JPA의 실제 구현체 중 하나이며, 현재 JPA구현체 중 가장 많이 사용됨

## 영속성 컨텍스트 (Persisnence Context)
영속성 컨텍스트는 JPA가 관리하는 엔티티 객체의 집합  
엔티티 객체가 영속 컨텍스트에 들어오게 되면 JPA는 엔티티 객체의 매핑정보를 가지고 DB에 반영함  
엔티티 객체가 영속 컨텍스트에 들어오게 되어 관리 대상이 되면 그 객체를 영속객체라고 부름  
* 영속성 컨텍스트는 세션단위로 생명주기를 갖고있다. ( 세션이 생기면서 만들어지고, 세션이 종료되면 없어짐)
* 영속성 컨텍스트에 접근하기 위해 EntityManager를 사용함
  *  EntityManager 생성(DB에 접근하기 위해) - EntityManagerFactory를 통해 생성
  * EntityManager가 가지고 있는 트랜잭션을 시작
  * EntityManager를 통해 영속 컨텍스트에 접근하고 객체작업
  * 트랜잭션을 커밋하여 DB에 반영
  * EntityManager 종료 

## 엔티티(Entity) 클래스
JPA어노테이션을 활용하여 엔티티 클래스를 정의 - DB구조와 동일하다고 생각하면됨
* @Entity : 해당 클래스가 JPA 엔티티클래스라고 정의 
* @Table :  해당 클래스가 데이터베이스의 어느 테이블에 매핑되는지 정의
* @Id
* @Column

## EntityManagerFactory
EntityManager를 생성하기 위한 팩토리 인터페이스로 persistence 단위별로 팩토리를 생성

## TypedQuery
JPA(Java Persistence API)에서 TypedQuery는 실행 결과를 타입 안정성(type-safe)을 보장하는 쿼리를 생성하는 인터페이스  

일반적인 JPQL(Java Persistence Query Language) 쿼리를 생성하는 것과 비슷하지만, TypedQuery는 반환되는 결과를 쿼리 실행 전에 정의된 엔티티 타입으로 지정할 수 있으며, 이를 통해 실행 결과의 타입 안정성을 보장할 수 있다.

* TypedQuery를 사용하여 Book 엔티티를 조회하는 코드
```java
TypedQuery<Book> query = em.createQuery(
  "SELECT b FROM Book b WHERE b.author = :author", Book.class);
query.setParameter("author", "J.K. Rowling");
List<Book> books = query.getResultList();
```
> 위의 코드에서 em은 엔티티 매니저(Entity Manager)를 나타내며, Book은 반환되는 결과의 엔티티 타입을 지정한 것이다.  
> getResultList() 메소드를 호출하면, 쿼리 결과로 Book 엔티티의 리스트가 반환
> TypedQuery를 사용하면, 반환되는 결과를 정확하게 타입 캐스팅할 수 있기 때문에, 불필요한 타입 체크를 방지할 수 있으며, 코드 가독성과 유지 보수성을 향상시킬 수 있다.






