package it.casestudy

case class ClusterInformation[V: Numeric](minPoint: ClusterData[V], maxPoint: ClusterData[V], centroid: ClusterData[V])
