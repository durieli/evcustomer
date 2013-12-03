/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package org.powertac.evcustomer.customers;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powertac.common.*;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.interfaces.TariffMarket;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TariffSubscriptionRepo;
import org.powertac.evcustomer.Config;
import org.powertac.evcustomer.PredictableRandom;
import org.powertac.evcustomer.beans.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;


/**
 * @author Govert Buijs
 * @version 0.5, Date: 2013.11.28
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:test-config.xml"})
@DirtiesContext
public class EvSocialClassTest
{
  @Autowired
  private TimeService timeService;

  @Autowired
  private CustomerRepo customerRepo;

  @Autowired
  private TariffRepo tariffRepo;

  @Autowired
  private TariffSubscriptionRepo tariffSubscriptionRepo;

  @Autowired
  private TariffMarket mockTariffMarket;

  private EvSocialClass evSocialClass;
  private SocialGroup socialGroup;
  private Map<Integer, SocialGroup> socialGroups;
  private Map<Integer, SocialGroupDetail> groupDetails;
  private Map<Integer, Activity> activities;
  private Activity activity;
  private Map<Integer, ActivityDetail> activityDetails;
  private ActivityDetail activityDetail;
  private List<Car> cars;
  private Car car;

  private String className = "Test SocialClass";
  private int groupId = 1;
  private String groupName = "Test SocialGroup";

  private int populationCount = 1;
  private double probability = 1;
  private double maleProbability = 0.5;

  private CustomerInfo info, info2;
  private TariffSpecification defaultTariffSpec, defaultTariffSpecEV;
  private Tariff defaultTariff, defaultTariffEV;

  private Instant now;

  private int seedId = 1;

  @Before
  public void setUp ()
  {
    evSocialClass = new EvSocialClass(className, timeService);
    socialGroup = new SocialGroup(groupId, groupName);
    socialGroups = new HashMap<Integer, SocialGroup>();
    activities = new HashMap<Integer, Activity>();
    activity = new Activity(0, "Test Activity", 1.0, 1.0);
    activityDetails = new HashMap<Integer, ActivityDetail>();
    activityDetail = new ActivityDetail(0, 50, 50, 1.0, 1.0);
    cars = new ArrayList<Car>();

    customerRepo.recycle();
    tariffSubscriptionRepo.recycle();
    tariffRepo.recycle();
    Broker broker1 = new Broker("Joe");

    now = new DateTime(2011, 1, 10, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
    timeService.setCurrentTime(now.toInstant());
    Instant exp = new Instant(now.getMillis() + TimeService.WEEK * 10);

    defaultTariffSpec =
        new TariffSpecification(broker1, PowerType.CONSUMPTION)
            .withExpiration(exp).withMinDuration(TimeService.WEEK * 8)
            .addRate(new Rate().withValue(-0.222));
    defaultTariff = new Tariff(defaultTariffSpec);
    defaultTariff.init();
    defaultTariff.setState(Tariff.State.OFFERED);

    defaultTariffSpecEV =
        new TariffSpecification(broker1, PowerType.ELECTRIC_VEHICLE)
            .withExpiration(exp).withMinDuration(TimeService.WEEK * 8)
            .addRate(new Rate().withValue(-0.121).withMaxCurtailment(0.3));
    defaultTariffEV = new Tariff(defaultTariffSpecEV);
    defaultTariffEV.init();
    defaultTariffEV.setState(Tariff.State.OFFERED);

    when(mockTariffMarket.getDefaultTariff(PowerType.CONSUMPTION))
        .thenReturn(defaultTariff);
    when(mockTariffMarket.getDefaultTariff(PowerType.ELECTRIC_VEHICLE))
        .thenReturn(defaultTariffEV);
  }

  @After
  public void tearDown ()
  {
    tariffRepo = null;

    defaultTariff = null;
    defaultTariffEV = null;
    defaultTariffSpec = null;
    defaultTariffSpecEV = null;

    evSocialClass = null;
    socialGroups = null;
    socialGroup = null;
    activities = null;
    activity = null;
    activityDetails = null;
    activityDetail = null;
    cars = null;
    car = null;
  }

  private void initializeClass ()
  {
    String base = evSocialClass.getName();
    info = new CustomerInfo(
        EvSocialClass.createInfoName(base, PowerType.CONSUMPTION), 1)
        .withPowerType(PowerType.CONSUMPTION);
    info2 = new CustomerInfo(
        EvSocialClass.createInfoName(base, PowerType.ELECTRIC_VEHICLE), 1)
        .withPowerType(PowerType.ELECTRIC_VEHICLE);

    socialGroups.put(groupId, socialGroup);
    activities.put(activity.getId(), activity);

    activityDetails.put(activityDetail.getActivityId(), activityDetail);
    Map<Integer, Map<Integer, ActivityDetail>> allActivityDetails =
        new HashMap<Integer, Map<Integer, ActivityDetail>>();
    allActivityDetails.put(socialGroup.getId(), activityDetails);

    car = new Car("TestCar", 100.0, 200.0, 20.0, 10.0);
    cars.add(car);

    evSocialClass.addCustomerInfo(info);
    evSocialClass.addCustomerInfo(info2);

    groupDetails = new HashMap<Integer, SocialGroupDetail>();
    groupDetails.put(groupId,
        new SocialGroupDetail(groupId, probability, maleProbability));

    evSocialClass.initialize(socialGroups, groupDetails, activities,
        allActivityDetails, cars, populationCount, seedId++);
  }

  @Test
  public void testInitialization ()
  {
    initializeClass();

    assertEquals(evSocialClass.getName(), className);
    assertEquals(evSocialClass.getCustomerInfo(),
        new ArrayList<CustomerInfo>()
        {{
            add(info);
            add(info2);
          }});
    assertEquals(evSocialClass.evCustomers.size(), populationCount);

    EvCustomer evCustomer = evSocialClass.evCustomers.get(0);

    assertEquals(evCustomer.getSocialGroup().getName(), groupName);
    assertEquals(evCustomer.getSocialGroup().getId(), groupId);
    assertEquals(evCustomer.getSocialGroup(), socialGroup);
    assertEquals(evCustomer.getActivities(), activities);
    assertEquals(evCustomer.getActivityDetails(), activityDetails);
    assertEquals(evCustomer.getCar(), car);
  }

  @Test
  public void testCreation ()
  {
    initializeClass();

    evSocialClass.addCustomerInfo(info);
    evSocialClass.addCustomerInfo(info2);

    assertNotNull("not null", evSocialClass);
    assertEquals("correct customerInfo size", 2,
        evSocialClass.getCustomerInfo().size());
    assertEquals("correct powerType for first", PowerType.CONSUMPTION,
        evSocialClass.getCustomerInfo().get(0).getPowerType());
    assertEquals("correct powerType for second",
        PowerType.ELECTRIC_VEHICLE,
        evSocialClass.getCustomerInfo().get(1).getPowerType());
    assertEquals("two customers on repo", 2, customerRepo.list().size());
  }

  @Test
  public void testRandomGroup ()
  {
    initializeClass();

    Random predictable = new PredictableRandom(new double[]{0}, new int[]{0});
    int groupId = evSocialClass.getRandomGroupId(groupDetails, predictable);

    assertEquals(groupId, groupDetails.get(1).getId());
  }

  @Test
  public void testDefaultSubscription1 ()
  {
    initializeClass();

    evSocialClass.addCustomerInfo(info);
    evSocialClass.addCustomerInfo(info2);

    evSocialClass.subscribeDefault();

    verify(mockTariffMarket).subscribeToTariff(defaultTariff, info, 1);
    verify(mockTariffMarket).subscribeToTariff(defaultTariffEV, info2, 1);
  }

  @Test
  public void testDefaultSubscription2 ()
  {
    initializeClass();

    final Map<Tariff, CustomerInfo> subscriptions =
        new HashMap<Tariff, CustomerInfo>();

    doAnswer(new Answer<Object>()
    {
      public Object answer (InvocationOnMock invocation)
      {
        Object[] args = invocation.getArguments();
        subscriptions.put((Tariff) args[0], (CustomerInfo) args[1]);
        return "Whatever";
      }
    }).when(mockTariffMarket).subscribeToTariff(
        isA(Tariff.class), isA(CustomerInfo.class), anyInt());

    when(mockTariffMarket.getDefaultTariff(PowerType.CONSUMPTION))
        .thenReturn(null);
    when(mockTariffMarket.getDefaultTariff(PowerType.ELECTRIC_VEHICLE))
        .thenReturn(null);

    subscriptions.clear();
    evSocialClass.subscribeDefault();
    assertEquals(subscriptions.size(), 0);

    when(mockTariffMarket.getDefaultTariff(PowerType.CONSUMPTION))
        .thenReturn(defaultTariff);
    subscriptions.clear();
    evSocialClass.subscribeDefault();
    assertEquals(subscriptions.size(), 1);
    assertEquals(subscriptions.get(defaultTariff), info2);

    when(mockTariffMarket.getDefaultTariff(PowerType.ELECTRIC_VEHICLE))
        .thenReturn(defaultTariffEV);
    subscriptions.clear();
    evSocialClass.subscribeDefault();
    assertEquals(subscriptions.size(), 2);
    assertEquals(subscriptions.get(defaultTariff), info);
    assertEquals(subscriptions.get(defaultTariffEV), info2);
  }

  @Test
  public void testActivities ()
  {
    maleProbability = 1;
    Random predictable = new PredictableRandom(new double[]{0}, new int[]{0});

    initializeClass();

    for (EvCustomer evCustomer : evSocialClass.evCustomers) {
      evCustomer.setRiskAttitude(0);
      evCustomer.setGenerator(predictable);
    }

    assertEquals(100, car.getCurrentCapacity(), 1E-6);
    evSocialClass.doActivities();
    assertEquals(98.958333, car.getCurrentCapacity(), 1E-6);
  }

  @Test
  public void testConsumePower ()
  {
    // Make sure the battery is not 100% full
    testActivities();

    double capacityBefore =
        evSocialClass.evCustomers.get(0).getCar().getCurrentCapacity();

    // Normally isDriving would be set by doActivity
    for (EvCustomer evCustomer : evSocialClass.evCustomers) {
      evCustomer.setDriving(false);
    }

    TariffSubscription ts = new TariffSubscription(info2, defaultTariffEV);
    ts.subscribe(info2.getPopulation());
    tariffSubscriptionRepo.add(ts);

    evSocialClass.consumePower();

    double capacityAfter =
        evSocialClass.evCustomers.get(0).getCar().getCurrentCapacity();

    assertEquals(capacityBefore, 98.958333, 1E-6);
    assertEquals(capacityAfter, 100, 1E-6);

    for (CustomerInfo customerInfo : evSocialClass.getCustomerInfo()) {
      List<TariffSubscription> subs = tariffSubscriptionRepo
          .findActiveSubscriptionsForCustomer(customerInfo);

      assertTrue("EvSocialClass consumed power for each customerInfo",
          subs.size() == 0 || subs.get(0).getTotalUsage() >= 0);
    }
  }

  @Test
  public void testTariffEvaluator ()
  {
    initializeClass();

    EvCustomer evCustomer = evSocialClass.evCustomers.get(0);
    Random generator = new PredictableRandom(new double[]{0, 1, 0, 1}, new int[]{0});
    double weight = 0.5;
    double weeks = Config.MAX_DEFAULT_DURATION;

    TariffEvaluator tariffEvaluator =
        evSocialClass.createTariffEvaluator(evCustomer, info, weight, weeks);

    assertEquals(tariffEvaluator.getInterruptibilityFactor(),
        Config.INTERRUPTIBILITY_FACTOR, 1E-6);
    assertEquals(tariffEvaluator.getTieredRateFactor(),
        Config.TIERED_RATE_FACTOR, 1E-6);
    assertEquals(tariffEvaluator.getTouFactor(),
        Config.TOU_FACTOR, 1E-6);
    assertEquals(tariffEvaluator.getVariablePricingFactor(),
        Config.VARIABLE_PRICING_FACTOR, 1E-6);

    TariffEvaluationWrapper wrapper =
        new TariffEvaluationWrapper(info, evCustomer, generator);

    assertEquals(info.getName(), wrapper.getCustomerInfo().getName());
    assertEquals(wrapper.getInertiaSample(), 0, 1E-6);
    assertEquals(wrapper.getInertiaSample(), 1, 1E-6);
    assertEquals(wrapper.getTariffChoiceSample(), 0, 1E-6);
    assertEquals(wrapper.getTariffChoiceSample(), 1, 1E-6);
    assertEquals(wrapper.getBrokerSwitchFactor(false),
        Config.BROKER_SWITCH_FACTOR, 1E-6);
    assertEquals(wrapper.getBrokerSwitchFactor(true),
        5 * Config.BROKER_SWITCH_FACTOR, 1E-6);

    double[] profile0 = wrapper.getCapacityProfile(defaultTariff);
    double[] profile1 = wrapper.getCapacityProfile(defaultTariffEV);

    assertEquals(profile0.length, profile1.length);
    for (int i = 0; i < profile0.length; i++) {
      double load = evCustomer.getDominantLoad() / Config.HOURS_OF_DAY;
      assertEquals(profile0[i], load, 1E-6);
      assertEquals(profile1[i], load, 1E-6);
    }
    assertEquals(profile0.length, 24);
  }
}