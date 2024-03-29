package ru.ots.moskow.metrobus;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.population.routes.CompressedNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.Dijkstra;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.*;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.vehicles.*;
import sun.nio.ch.Net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Created by Ekaterina on 04.09.2016.
 */
public class LineCreator {
    private final Network network;
    private final String routeName;
    private final String [] routeNodes;
    private final String [] stopLinks;
    private final Integer opening;
    private final Integer closing;
    private final Integer frequency;
    private final String mode;
    private final Scenario scenario;

    private final List<Link> routeLinks = new ArrayList<>();
    private final List<Link> dedicatedRouteLinks = new ArrayList<>();
    private final List<Link> linkForReduce = new ArrayList<>();
    private final List<TransitRouteStop> stops = new ArrayList<>();
    private TransitRoute transitRoute;
    private NetworkFactory networkFactory;

    public LineCreator(Network network, String routeName, String[] routeNodes, String[] stopLinks, Integer open, Integer close, Integer fr, String md, Scenario scenario) {
        this.network = network;
        this.routeName = routeName;
        this.routeNodes = routeNodes;
        this.stopLinks = stopLinks;
        this.opening = open;
        this.closing = close;
        this.frequency = fr;
        this.mode = md;
        this.scenario = scenario;
    }

    public void createWithDedicatedLanes(){
        prepareNetwork();
        obtainRouteLinks();
        createDedicatedLinks();
        reduceMainLinkLines();
        createStops();
        createLineAndRoute();
        createDepartures();
    }

    public void createOnExistingLanes(){
        prepareNetwork();
        obtainRouteLinks();
        createStops();
        createLineandRouteWithoutDedicatedWay();
        createDepartures();
    }

    private void prepareNetwork() {
        network.getLinks().values().forEach(link -> normalizeLink(link));
    }

    private void normalizeLink(Link link) {
        double length = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());
        link.setLength(length);
    }

    private void createLineandRouteWithoutDedicatedWay () {
        TransitSchedule transitSchedule = scenario.getTransitSchedule();
        TransitScheduleFactory transitScheduleFactory = transitSchedule.getFactory();
        TransitLine transitLine = transitScheduleFactory.createTransitLine(Id.create(routeName, TransitLine.class));
        transitSchedule.addTransitLine(transitLine);
        List<Id<Link>> linkIds = new ArrayList<>();
        routeLinks.stream().forEach(link -> linkIds.add(link.getId()));
        NetworkRoute networkRoute = RouteUtils.createNetworkRoute(linkIds, network);
        String mode = this.mode;
        transitRoute = transitScheduleFactory.createTransitRoute(Id.create(routeName, TransitRoute.class), networkRoute, stops, mode);
        transitLine.addRoute(transitRoute);
    }

    private void createLineAndRoute() {
        TransitSchedule transitSchedule = scenario.getTransitSchedule();
        TransitScheduleFactory transitScheduleFactory = transitSchedule.getFactory();
        TransitLine transitLine = transitScheduleFactory.createTransitLine(Id.create(routeName, TransitLine.class));
        transitSchedule.addTransitLine(transitLine);
        List<Id<Link>> linkIds = new ArrayList<>();
        dedicatedRouteLinks.stream().forEach(link -> linkIds.add(link.getId()));
        NetworkRoute networkRoute = RouteUtils.createNetworkRoute(linkIds, network);
        String mode = "bus";
        transitRoute = transitScheduleFactory.createTransitRoute(Id.create(routeName, TransitRoute.class), networkRoute, stops, mode);
        transitLine.addRoute(transitRoute);
    }

    private void createDepartures() {
        TransitSchedule transitSchedule = scenario.getTransitSchedule();
        TransitScheduleFactory transitScheduleFactory = transitSchedule.getFactory();
        Vehicles vehicles = scenario.getTransitVehicles();
        VehiclesFactory factory = vehicles.getFactory();

        VehicleType vehicleTypeBus = factory.createVehicleType(Id.create(routeName, VehicleType.class));
        VehicleCapacity capacityBus = factory.createVehicleCapacity();
        capacityBus.setSeats(44);
        capacityBus.setStandingRoom(44);
        vehicleTypeBus.setCapacity(capacityBus);
        vehicleTypeBus.setMaximumVelocity(60/3.6);

        VehicleType vehicleTypeTrain = factory.createVehicleType(Id.create(routeName, VehicleType.class));
        VehicleCapacity capacityTrain = factory.createVehicleCapacity();
        capacityTrain.setSeats(864);
        capacityTrain.setStandingRoom(736);
        vehicleTypeTrain.setCapacity(capacityTrain);
        vehicleTypeTrain.setMaximumVelocity(60/3.6);

        VehicleType vehicleTypeMetro = factory.createVehicleType(Id.create(routeName, VehicleType.class));
        VehicleCapacity capacityMetro = factory.createVehicleCapacity();
        capacityMetro.setSeats(264);
        capacityMetro.setStandingRoom(1080);
        vehicleTypeMetro.setCapacity(capacityMetro);
        vehicleTypeMetro.setMaximumVelocity(60/3.6);

        VehicleType vehicleTypeLrt = factory.createVehicleType(Id.create(routeName, VehicleType.class));
        VehicleCapacity capacityLrt = factory.createVehicleCapacity();
        capacityLrt.setSeats(58);
        capacityLrt.setStandingRoom(135);
        vehicleTypeLrt.setCapacity(capacityLrt);
        vehicleTypeLrt.setMaximumVelocity(60/3.6);

        switch (mode){
            case "train":
                vehicles.addVehicleType(vehicleTypeTrain);
                break;
            case "metro":
                vehicles.addVehicleType(vehicleTypeMetro);
                break;
            case "lrt":
                vehicles.addVehicleType(vehicleTypeLrt);
                break;
            default:
                vehicles.addVehicleType(vehicleTypeBus);
        }

        for (int i = opening; i < closing; i+=frequency){
            Departure departure = transitScheduleFactory.createDeparture(Id.create(routeName + i, Departure.class), i * 60);
            Id<Vehicle> vehicleId = Id.createVehicleId(routeName + i);
            Vehicle veh = factory.createVehicle(vehicleId, vehicleTypeLrt);
            vehicles.addVehicle(veh);
            departure.setVehicleId(vehicleId);
            transitRoute.addDeparture(departure);
        }
    }

    private void createStops() {
        long id = 0;
        TransitSchedule transitSchedule = scenario.getTransitSchedule();
        TransitScheduleFactory factory = transitSchedule.getFactory();
        double arrivalOffset = 0;
        for (int i = 0; i < stopLinks.length; i++){
            Link stopLink = network.getLinks().get(Id.createLinkId(mode + stopLinks[i]));
            if (stopLink == null){
                System.out.println("No link with id " + stopLinks[i]);
                continue;
            }
            if (i != 0){
                Link fromLinkId = network.getLinks().get(Id.createLinkId(mode + stopLinks[i - 1]));
                if (fromLinkId == null){
                    System.out.println("No link with id " + stopLinks[i-1]);
                    continue;
                }
                double distance = CoordUtils.calcEuclideanDistance(stopLink.getCoord(), fromLinkId.getCoord());
                arrivalOffset += distance / ( 50 / 3.6);
            }
            TransitStopFacility facility = factory.createTransitStopFacility(Id.create(routeName + (id++),
                    TransitStopFacility.class), stopLink.getCoord(), false);
            facility.setLinkId(stopLink.getId());
            transitSchedule.addStopFacility(facility);
            TransitRouteStop transitRouteStop = factory.createTransitRouteStop(facility, arrivalOffset,arrivalOffset + 30);
            transitRouteStop.setAwaitDepartureTime(true);
            stops.add(transitRouteStop);
        }
    }

    private void reduceMainLinkLines() {
        linkForReduce.stream().forEach(link -> reduceLinkCapacityAndLanes(link));
    }

    private void reduceLinkCapacityAndLanes(Link link) {
        double lanes = link.getNumberOfLanes();
        double capacity = link.getCapacity();
        if (lanes > 6){
            link.setNumberOfLanes(lanes - 1);
            link.setCapacity(capacity*(lanes - 1) / lanes);
        } else
        {
            link.setNumberOfLanes(link.getNumberOfLanes());
            link.setCapacity(link.getCapacity());
        }
    }

    private void createDedicatedLinks() {
        routeLinks.stream().forEach(link -> createDedicatedLink(link));
    }

    private void createDedicatedLink(Link link) {
        networkFactory = network.getFactory();
        Id<Link> linkId = Id.createLinkId("brt_" + link.getId().toString());
        Link dedicatedLink = network.getLinks().get(linkId);
        if (dedicatedLink == null) {
            dedicatedLink = createDedicatedLink(link, linkId);
            linkForReduce.add(link);
        }
        dedicatedRouteLinks.add(dedicatedLink);
    }

    private Link createDedicatedLink(Link link, Id<Link> linkId) {
        Link dedicatedLink;
        Link brtLink = networkFactory.createLink(linkId, link.getFromNode(), link.getToNode());
        HashSet<String> set = new HashSet<>();
        set.add("bus");
        brtLink.setAllowedModes(set);
        brtLink.setFreespeed(60/3.6);
        brtLink.setCapacity(15000);
        brtLink.setNumberOfLanes(1);
        brtLink.setFreespeed(link.getFreespeed());
        brtLink.setLength(link.getLength());
        network.addLink(brtLink);
        dedicatedLink = brtLink;
        return dedicatedLink;
    }

    private void obtainRouteLinks() {
        Network network = scenario.getNetwork();
        PreProcessDijkstra preProcessData = new PreProcessDijkstra();
        preProcessData.run(network);
        TravelTime travelTime = new FreespeedTravelTimeAndDisutility(1,1,1);
        TravelDisutility costFunction = new OnlyTimeDependentTravelDisutility(travelTime);
        LeastCostPathCalculator routingAlgo = new Dijkstra(network, costFunction, travelTime);
        for (int i = 0; i < routeNodes.length - 1; i++){
            Node fromNode = network.getNodes().get(Id.createNodeId(routeNodes[i]));
            Node toNode = network.getNodes().get(Id.createNodeId(routeNodes[i + 1]));
            LeastCostPathCalculator.Path path = routingAlgo.calcLeastCostPath(fromNode, toNode, 0,
                    scenario.getPopulation().getFactory().createPerson(Id.createPersonId(1)), new VehicleImpl(Id.createVehicleId(1), new VehicleTypeImpl(
                            Id.create(1, VehicleType.class))));
            path.links.forEach(link -> routeLinks.add(link));
        }
    }


}
