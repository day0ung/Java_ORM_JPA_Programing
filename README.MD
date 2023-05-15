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

