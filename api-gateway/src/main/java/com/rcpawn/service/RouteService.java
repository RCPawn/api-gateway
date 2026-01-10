package com.rcpawn.service;

import com.rcpawn.entity.GatewayRouteEntity;

import java.util.List;

public interface RouteService {
    List<GatewayRouteEntity> listRoutes() throws Exception;
    void updateRoute(GatewayRouteEntity route) throws Exception;
    void deleteRoute(String id) throws Exception;
}