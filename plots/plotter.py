#!/usr/bin/env python

################################################################################
########################### Script background info #############################
################################################################################

# - https://stackoverflow.com/questions/4455076/how-to-access-the-ith-column-of-a-numpy-multidimensional-array

###############################################################################
############################## Script imports #################################
###############################################################################

import sys
import numpy as np
import matplotlib.pyplot as plt
import os
from os import listdir
from os.path import isfile, join
import re
import pylab as pl                   # frange
import math                          # isnan, isinf, ceil
import pprint
from collections import defaultdict
import ruamel.yaml as yaml
from textwrap import wrap
#import copy  # copy.deepcopy(myDict)
#import fnmatch # for fnmatch.fnmatch(str,glob)
from functools import reduce
import pdb
import copy # for deepcopy

#################################################################################
############################## Script functions #################################
#################################################################################
data_per_line = 2
# returns: list of full paths of files (under directory `basedir`) filtered through a file prefix (`basefn`)
def get_data_files(basedir, fileregex):
  return [join(basedir,p) for p in listdir(basedir) if isfile(join(basedir,p)) and re.match(fileregex,p)]

def isAccepted(s):
  # todo improve this, add all the seed in a simulation
  return len(s) > 0 and s[0] != "#" and s[0] != " " and "seed" not in s

def remove_tuple_item_at_index(tpl,i):
  return tpl[0:i]+tpl[(i+1):len(tpl)]

# configs: list where each config is an N-dim tuple of (dim,val) tuples
# returns: dict where keys are (dims-dim) and values are lists of configs
def group_by_varying_values_of(dim, configs):
  res = defaultdict(list)
  print("config = ", configs)
  for config in configs:
    configWithoutDim = tuple([d for d in config if d[0]!=dim])
    res[configWithoutDim].append(config)
  return res

# Globals used
#   - t_start: beginning of time
#   - t_step: length of a bucket
#def bucket_pos(value):
#  math.ceil((value-t_start)/t_step)-1

# Returns a dict
def process_files(filepaths):
  return dict([process_file(fp) for fp in filepaths])

# Returns a pair (id,matrix) for each parsed file
def process_file(filepath):
  print(("\n>>> Processing file: " + filepath))
  # Open file handle
  fh = open(filepath, "r")

  # Deduce some info from file name
  parts = re.findall('_+([^-]+)-(\d+\.?\d*)', filepath.replace(join(basedir,basefn),''))
  parts = [(p[0],format(float(p[1]),'.6f').rstrip('0')) for p in parts]
  parts = tuple(parts) # this must be hashable (and lists are not)
  print(("Dimensions: " + "; ".join([str(x) for x in parts])))
  parts_suffix = "_".join(map("-".join,parts))
  title = "; ".join(map("=".join,parts))

  # Gets the matrix (time X exports) from file content
  # | time | export1 | ... | exportN |
  # ----------------------------------
  # |  t1  |    .    | ... |    .    |
  # | .... |   ...   | ... |   ...   |
  # |  tK  |    .    | ... |    .    |
  # ----------------------------------
  matrix = process_file_content(fh)
  dimMatrix = matrix.transpose()

  # Closes file handle
  fh.close()

  return (parts, dimMatrix)

def process_file_content(filehandle):
  # Read data
  lines = filehandle.readlines()
  # Removes empty and comment lines and maps to float
  data_rows = np.array([list(map(float, s.strip().split(" "))) for s in lines if isAccepted(s)], dtype='float')
  return data_rows

def do_bucketize(contents, nbuckets=100, start=None, end=None):
  res = dict()
  for config, content in list(contents.items()):
    time = content[0]
    if start==None:
      start = time[0]
    if end==None:
      end = time[-1]
    time_bins = np.linspace(start,end,nbuckets)
    hist = np.histogram(time, time_bins)
    # for ncol,data in enumerate(content):
  # INCOMPLETE DEFINITION
  return res

def merge_samples(contents, configs):
  res = dict()
  for config, sconfigs in list(configs.items()):
    nsamples = len(sconfigs)
    print(("\tCONFIGURATION: " + str(config) + " has " + str(nsamples) + " samples."))

    matrices = [contents[sample_config] for sample_config in sconfigs]
    time = list([round(x) for x in matrices[0][0]])  # time should be the same for all
    matrices = list([l[1:] for l in matrices])  # skips the time dimension for each sample
    # Assumption: the position of values in matrices reflects the time in a consistent manner

    # Printing statistics
    # nplots = len(the_plots_labels)
    # stats = dict()
    # for expdim in range(0,nplots-1): # without 'time', which should be at index 0
    #  for m in matrices:
    #    curdata = m[expdim]
    #    curstats = stats.get(expdim, np.zeros(len(curdata)))
    #    stats[expdim] = curstats + curdata
    # print(stats)

    # Crop the matrices so that they have the same shape (i.e., the minimum shape of all the involved matrices)
    s = reduce(lambda s, m: min(s, m), [m.shape for m in matrices])  # uniform shape
    matrices = [m[:s[0], :s[1]] for m in matrices]
    time = time[:s[1]]

    # Merge the matrices
    merged = reduce(lambda a, b: a + b, matrices)
    mean = list([x / nsamples for x in merged])
    std_dev = map(lambda a: (a - mean) ** 2, matrices)
    std_dev = reduce(lambda a, b: a + b, std_dev)
    std_dev = list([np.sqrt(x / nsamples) for x in std_dev])
    for i, value in enumerate(merged):
      ##std.append(current_std)
      mean[i] = (mean[i], std_dev[i])
    mean.insert(0, time)  # reinserts time
    res[config] = mean
  return res

def plot(config,content,nf,pformat):
  title = list(map("=".join,config))
  if doWrap is not None: title = wrap("    ".join(title), 30)
  all_world = title
  title = ""
  for k, s in enumerate(all_world):
    if excluded_titles[nf] != None and k not in excluded_titles[nf]:
      title = title + s.strip() + " "
    if k % data_per_line == 0 and k > 0:
      title = title + "\n"
  #title = "\n".join([s.strip() for k,s in enumerate(title) if excluded_titles[nf] != None and k not in excluded_titles[nf]])

  parts_suffix = "_".join(map("-".join,config))

  plt.figure() # (figsize=(10,10), dpi=80)
  plt.xlabel(the_plots_labels[pformat[0]])
  plt.ylabel(y_labels[nf] if len(y_labels)>nf else "")
  maxy = float("-inf")
  for k in range(1,len(pformat)): # skip x-axis which is at pos 0
      #pdb.set_trace()
      mean, std = content[pformat[k]]
      #mean = content[pformat[k]]
      plt.plot(content[pformat[0]], mean, color=the_plots_colors[nf][pformat[k]], label=the_plots_labels[pformat[k]], linewidth=line_widths[nf][pformat[k]],
               linestyle=line_styles[nf][pformat[k]])
      plt.fill_between(content[pformat[0]], mean - std, mean + std, color=the_plots_colors[nf][pformat[k]], alpha=0.2)
      maxy = max(maxy, np.nanmax(mean))
  maxy = min(maxy+10, limitPlotY[nf])
  if nf in forceLimitPlotY: maxy = forceLimitPlotY[nf]
  axes = plt.gca()
  axes.set_ylim(ymax = maxy, ymin = startPlotY[nf])
  if nf in forceLimitPlotX: axes.set_xlim(xmax = forceLimitPlotX[nf])
  legend = plt.legend(loc= legendPosition[nf] if nf in legendPosition else 'upper right', prop={'size': legend_size},
                      bbox_to_anchor=legendBBoxToAnchor[nf] if nf in legendBBoxToAnchor else None, ncol = legendColumns[nf] if nf in legendColumns else 1)
  if nf in hlines:
      for hline in hlines[nf]:
          print(hline)
          y = hline[0]
          kwargs = hline[1]
          plt.axhline(y, **kwargs)
  if nf in vlines:
      for vline in vlines[nf]:
          x = vline[0]
          kwargs = vline[1]
          plt.axvline(x, **kwargs)
  t = plt.title(base_title+title_prefix[nf]+" \n "+title)
  plt.subplots_adjust(top=.84)
  suffix = (suffixes[nf] if nf in suffixes else "".join(map(str,pformat))) + "_" + parts_suffix
  savefn = outdir+basefn+"_"+str(nf)+"_"+suffix +"." + figFormat
  print(("SAVE: " + savefn))
  plt.tight_layout()
  if nf in exportLegend and exportLegend[nf]==True:
      legendsavefn = outdir+basefn+"_"+str(nf)+"_legend." + figFormat
      export_legend(legend, legendsavefn)
  plt.savefig(savefn, bbox_inches='tight', pad_inches = 0, format=figFormat)
  plt.close()

def export_legend(legend, filename="legend.pdf"):
    fig  = legend.figure
    fig.canvas.draw()
    bbox  = legend.get_window_extent().transformed(fig.dpi_scale_trans.inverted())
    fig.savefig(filename, dpi="figure", bbox_inches=bbox, format=figFormat)

pp = pprint.PrettyPrinter(indent=4) # for logging purposes

#######################################################################################
############################## Script configuration ###################################
#######################################################################################

sampling = True  # tells if there is a 'random' dimension for sampling
bucketize = False # tells if there is a 'time' dimension to be split into buckets
do_aggr_plotting = True
forceLimitPlotY = None
forceLimitPlotX = None
doWrap = None
limitPlotY = {}
fill_between = []
default_colors = ["black","red","blue","green"]
the_plots_labels = []
the_plots_formats = []
the_plots_colors = []
line_widths = []
line_styles = []
title_prefix = ""

###################################################################################
############################## Script preparation #################################
###################################################################################

script = sys.argv[0]
if len(sys.argv)<5:
  print("USAGE: plotter <plotConfig> <basedir> <fileregex> <basefn>")
  exit(0)

plotconfig = sys.argv[1]
basedir = sys.argv[2]
fileregex = sys.argv[3]
basefn = sys.argv[4]
outdir = os.path.join(sys.argv[5],'') if len(sys.argv)>=7 else os.path.join(basedir, "imgs/")
base_title = sys.argv[6]

if not os.path.exists(outdir):
  os.makedirs(outdir)

files = get_data_files(basedir,fileregex)

print(("Executing script: basedir=" + basedir + "\t fileregex=" + fileregex))
print(("Files to be processed: " + str(files)))
print(("Output directory for graphs: " + str(outdir)))
print(("Loading plot configurartion: " + str(plotconfig)))

############################# Plot configuration

def parse_sim_option(pc, option, default=None):
    opt = pc.get(option)
    if type(opt) is dict:
        defval = opt[list(opt.keys())[-1]]
        opt = defaultdict(lambda: defval, opt)
    elif type(opt) is list:
        defval = opt[-1]
        opt = defaultdict(lambda: defval, dict(enumerate(opt)))
    elif not opt:
        opt = defaultdict(lambda: default)
    else: # single value
        defval = opt
        opt = defaultdict(lambda: defval)
    print((option + " >> " + str(opt)))
    return opt

with open(plotconfig, 'r') as stream:
    try:
        pc = yaml.load(stream, Loader=yaml.Loader)
        figFormat = pc.get('format','pdf')
        the_plots_labels = pc['the_plots_labels']
        the_plots_formats = pc['the_plots_formats']
        the_plots_colors = parse_sim_option(pc, 'the_plots_colors')
        suffixes = parse_sim_option(pc, 'file_suffixes')
        line_widths = parse_sim_option(pc, 'line_widths')
        line_styles = parse_sim_option(pc, 'line_styles')
        limitPlotY = parse_sim_option(pc, 'limit_plot_y', float('inf'))
        startPlotY = parse_sim_option(pc, 'start_plot_y', 0)
        forceLimitPlotY = parse_sim_option(pc, 'force_limit_plot_y')
        forceLimitPlotX = parse_sim_option(pc, 'force_limit_plot_x')
        legendPosition = parse_sim_option(pc, 'legend_position')
        exportLegend = parse_sim_option(pc, 'export_legend')
        hlines = parse_sim_option(pc, 'hlines')
        vlines = parse_sim_option(pc, 'vlines')
        legendBBoxToAnchor = parse_sim_option(pc, 'legend_bbox_to_anchor')
        legendColumns = parse_sim_option(pc, 'legend_columns')
        y_labels = pc.get('y_labels',[])
        legend_size = pc.get('legend_size',10)
        #sampling = pc.get('sampling', False)
        sampling = parse_sim_option(pc, 'sampling')
        sampling_dim = parse_sim_option(pc, 'samplingField', 'random')
        excluded_titles = parse_sim_option(pc, 'excluded_titles')
        title_prefix = parse_sim_option(pc, 'title_prefix', '')
        doWrap = pc.get('do_wrap')
        plt.rcParams.update({'font.size': pc.get('font_size', 14)})
    except yaml.YAMLError as exc:
        print(exc)
        exit(1)

############################# Script logic

print('*************************')
print('*** PER FILE PLOTTING ***')
print('*************************')

# CONTENTS: a dict from file descriptors (dimension k/v pairs) to file contents (matrix data)
#   Dictionary {key => matrix}
#   file1 [d1=A  d2=B ] => export1=[...], ..., exportK=[...]
#   file2 [d1=A' d2=B ] => export1=[...], ..., exportK=[...]
#   file3 [d1=A  d2=B'] => export1=[...], ..., exportK=[...]
#   file4 [d1=A' d2=B'] => export1=[...], ..., exportK=[...]
contents = process_files(files)

# CONFIGURATIONS
#   file1_2 [d1=*, d2=B ] => export1=[...], ..., exportK=[...]
#   file3_4 [d1=*, d2=B'] => export1=[...], ..., exportK=[...]
configs = list(contents.keys()) # List of configs, where each config is an N-dim tuple of (k,v) tuples
# if sampling:
#   # Let's group configurations (individual datasets) into groups where only a sampling dimension varies
#   # sconfigs is a dict where keys are (dims-'random') and values are lists of configs
#   sconfigs = group_by_varying_values_of(sampling_dim, configs)
#
#   merged_contents = merge_samples(contents, sconfigs)
#   for title,content in merged_contents.items():
#     plot(title,content)
# else:
#   allcontents = dict()
#   for nf, pformat in enumerate(the_plots_formats):
#     allcontents[nf] = content
#   for title,content in contents.items(): plot(title,allcontents)

for nf, pformat in enumerate(the_plots_formats):
  c = copy.deepcopy(contents)
  print(nf)
  if nf in sampling and sampling[nf] == True:
    print((str(nf) + " is to be sampled"))
    sconfigs = group_by_varying_values_of(sampling_dim[nf], configs)
    c = merge_samples(c, sconfigs)
  else:
    print((str(nf) + " is NOT to be sampled"))
  for title, content in list(c.items()):
    plot(title, content, nf, pformat)


if bucketize:
  contents = do_bucketize(contents)
