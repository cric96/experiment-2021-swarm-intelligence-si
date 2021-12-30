#!/bin/zsh
autoload zmv
folder=$1
echo "renaming $folder.."

printf "."
zmv $folder/'(*)speed(*)' $folder/'$1ω$2'
printf "."
zmv $folder/'(*)density(*)' $folder/'$1α$2'
printf "."
zmv $folder/'(*)in_cluster_thr(*)' $folder/'$1θ$2'
printf "."
zmv $folder/'(*)same_cluster_thr(*)' $folder/'$1γ$2'
printf "."
zmv $folder/'(*)candidate_in_hysteresis(*)' $folder/'$1β$2'
printf "."
zmv $folder/'(*)explore_area(*)' $folder/'$1ζ$2'
printf "."
zmv $folder/'(*)0.02(*)' $folder/'$1{7}$2'
printf "."
zmv $folder/'(*)0.03(*)' $folder/'$1{10}$2'
printf "."
zmv $folder/'(*)0.04(*)' $folder/'$1{14}$2'
printf "."
zmv $folder/'(*){(*)}(*)' $folder/'$1$2$3'
echo ""
