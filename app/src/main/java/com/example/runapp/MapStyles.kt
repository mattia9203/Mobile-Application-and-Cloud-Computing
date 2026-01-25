package com.example.runapp

import com.google.android.gms.maps.model.MapStyleOptions

val DarkMapStyle = MapStyleOptions("""
[
  {
    "elementType": "geometry",
    "stylers": [{ "color": "#242f3e" }]
  },
  {
    "elementType": "labels.text.stroke",
    "stylers": [{ "color": "#242f3e" }]
  },
  {
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#746855" }]
  },
  {
    "featureType": "poi",
    "elementType": "geometry",
    "stylers": [{ "visibility": "on" }, { "color": "#263c3f" }]
  },
  {
    "featureType": "poi",
    "elementType": "labels.text.fill",
    "stylers": [{ "visibility": "on" }, { "color": "#d59563" }]
  },
  {
    "featureType": "poi",
    "elementType": "labels.icon",
    "stylers": [{ "visibility": "on" }] 
  },
  {
    "featureType": "poi.business",
    "stylers": [{ "visibility": "on" }] 
  },
  {
    "featureType": "road",
    "elementType": "geometry",
    "stylers": [{ "color": "#38414e" }]
  },
  {
    "featureType": "road",
    "elementType": "geometry.stroke",
    "stylers": [{ "color": "#212a37" }]
  },
  {
    "featureType": "road",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#9ca5b3" }]
  },
  {
    "featureType": "water",
    "elementType": "geometry",
    "stylers": [{ "color": "#17263c" }]
  },
  {
    "featureType": "water",
    "elementType": "labels.text.fill",
    "stylers": [{ "color": "#515c6d" }]
  }
]
""")